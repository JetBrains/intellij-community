package com.intellij.util.indexing;

import com.intellij.ide.startup.BackgroundableCacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.FileContentQueue;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 29, 2008
 */
public class UnindexedFilesUpdater implements BackgroundableCacheUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.UnindexedFilesUpdater");
  private static final Key<Boolean> DONT_INDEX_AGAIN_KEY = Key.create("DONT_INDEX_AGAIN_KEY");
  private final FileBasedIndex myIndex;
  private final Project myProject;
  private final ProjectRootManager myRootManager;

  public UnindexedFilesUpdater(final Project project, final ProjectRootManager rootManager, FileBasedIndex index) {
    myIndex = index;
    myProject = project;
    myRootManager = rootManager;
  }

  public VirtualFile[] queryNeededFiles() {
    CollectingContentIterator finder = myIndex.createContentIterator();
    iterateIndexableFiles(finder);
    final List<VirtualFile> files = finder.getFiles();
    for (Iterator<VirtualFile> virtualFileIterator = files.iterator(); virtualFileIterator.hasNext();) {
      VirtualFile file = virtualFileIterator.next();
      if (file.getUserData(DONT_INDEX_AGAIN_KEY) != null) {
        virtualFileIterator.remove();
      }
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public boolean initiallyBackgrounded() {
    if (ApplicationManager.getApplication().isCommandLine() || ApplicationManager.getApplication().isUnitTestMode()) return false;
    return Registry.get(DumbServiceImpl.FILE_INDEX_BACKGROUND).asBoolean();
  }

  public boolean canBeSentToBackground(Collection<VirtualFile> remaining) {
    if (remaining.size() < 42) {
      return false;
    }

    final RegistryValue value = Registry.get(DumbServiceImpl.FILE_INDEX_BACKGROUND);
    if (!value.asBoolean()) {
      if (Messages.showDialog(myProject, "<html>" +
                                         "While indices are updated in background, most of IntelliJ IDEA's<br>" +
                                         "smart functionality <b>won't be available</b>.<br>" +
                                         "Only the most basic editing and version control operations will be enabled.<br>" +
                                         "There will be no Goto Class, no error highlighting, <b>no refactorings</b>, etc!<br>" +
                                         "Do you still want to send indexing to background?</html>", "Are you really sure?",
                              new String[]{"Yes", "No"}, 1, UIUtil.getWarningIcon()) != 0) {
        return false;
      }
    }

    value.setValue("true");

    return true;
  }

  public void backgrounded(final Collection<VirtualFile> remaining) {
    DumbServiceImpl.getInstance(myProject).queueIndexUpdate(new Consumer<ProgressIndicator>() {
      public void consume(final ProgressIndicator indicator) {
        final MyFileContentQueue queue = new MyFileContentQueue();

        try {
          final double count = remaining.size();
          queue.queue(remaining, indicator);

          final Consumer<VirtualFile> uiUpdater = new Consumer<VirtualFile>() {
            final Set<VirtualFile> processed = new THashSet<VirtualFile>();

            public void consume(VirtualFile virtualFile) {
              indicator.checkCanceled();
              indicator.setFraction(processed.size() / count);
              processed.add(virtualFile);
              indicator.setText2(virtualFile.getPresentableUrl());
            }
          };

          while (!myProject.isDisposed()) {
            indicator.checkCanceled();
            if (runUntilNextWriteAction(queue, uiUpdater)) {
              break;
            }
          }
        }
        finally {
          queue.clear();
          updatingDone();
        }
      }
    });
  }

  private boolean runUntilNextWriteAction(final MyFileContentQueue queue, final Consumer<VirtualFile> updateUi) {
    final ProgressIndicatorBase innerIndicator = new ProgressIndicatorBase();
    final ApplicationAdapter canceller = new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(Object action) {
        innerIndicator.cancel();
      }
    };

    final Ref<Boolean> finished = Ref.create(Boolean.FALSE);
    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().addApplicationListener(canceller);
        try {
          while (true) {
            if (myProject.isDisposed()) return;

            final FileContent fileContent = queue.take();
            if (fileContent == null) {
              finished.set(Boolean.TRUE);
              return;
            }

            final VirtualFile file = fileContent.getVirtualFile();
            if (file == null) {
              finished.set(Boolean.TRUE);
              return;
            }

            try {
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                public void run() {
                  innerIndicator.checkCanceled();

                  if (!file.isValid()) {
                    return;
                  }

                  updateUi.consume(file);

                  doProcessFile(fileContent);

                  innerIndicator.checkCanceled();
                }
              });
            }
            catch (ProcessCanceledException e) {
              queue.pushback(fileContent);
              return;
            }
            catch (NoProjectForFileException ignored) {
              return;
            }
            catch (Throwable e) {
              LOG.error("Error while indexing " + file.getPresentableUrl() + "\n" + "To reindex this file IDEA has to be restarted",
                        e);
              file.putUserData(DONT_INDEX_AGAIN_KEY, Boolean.TRUE);
            }
          }
        }
        finally {
          ApplicationManager.getApplication().removeApplicationListener(canceller);
        }
      }
    }, innerIndicator);

    return finished.get().booleanValue();
  }

  public void processFile(final FileContent fileContent) {
    doProcessFile(fileContent);
    IndexingStamp.flushCache();
  }

  private void doProcessFile(FileContent fileContent) {
    fileContent.putUserData(FileBasedIndex.PROJECT, myProject);
    myIndex.indexFileContent(fileContent);
  }

  private void iterateIndexableFiles(final ContentIterator processor) {
    final ProjectFileIndex projectFileIndex = myRootManager.getFileIndex();
    // iterate associated libraries
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    // iterate project content
    projectFileIndex.iterateContent(processor);

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    Set<VirtualFile> visitedRoots = new HashSet<VirtualFile>();
    for (IndexedRootsProvider provider : Extensions.getExtensions(IndexedRootsProvider.EP_NAME)) {
      //important not to depend on project here, to support per-project background reindex
      // each client gives a project to FileBasedIndex
      final Set<String> rootsToIndex = provider.getRootsToIndex();
      for (String url : rootsToIndex) {
        final VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(url);
        if (visitedRoots.add(root)) {
          iterateRecursively(root, processor, indicator);
        }
      }
    }
    for (Module module : modules) {
      OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
          final VirtualFile[] libSources = orderEntry.getFiles(OrderRootType.SOURCES);
          final VirtualFile[] libClasses = orderEntry.getFiles(OrderRootType.CLASSES);
          for (VirtualFile[] roots : new VirtualFile[][]{libSources, libClasses}) {
            for (VirtualFile root : roots) {
              if (visitedRoots.add(root)) {
                iterateRecursively(root, processor, indicator);
              }
            }
          }
        }
      }
    }
  }

  private static void iterateRecursively(@Nullable final VirtualFile root, final ContentIterator processor, ProgressIndicator indicator) {
    if (root != null) {
      if (indicator != null) {
        indicator.setText("Scanning files to index");
        indicator.setText2(root.getPresentableUrl());
      }

      if (root.isDirectory()) {
        for (VirtualFile file : root.getChildren()) {
          if (file.isDirectory()) {
            iterateRecursively(file, processor, indicator);
          }
          else {
            processor.processFile(file);
          }
        }
      } else {
        processor.processFile(root);
      }
    }
  }

  public void updatingDone() {
    myIndex.flushCaches();
  }

  public void canceled() {
    myIndex.flushCaches();
  }

  private static class MyFileContentQueue extends FileContentQueue {
    @Nullable private FileContent myBuffer;

    @Override
    protected void addLast(VirtualFile file) throws InterruptedException {
      IndexingStamp.flushCache();
      super.addLast(file);
    }

    @Override
    public FileContent take() {
      final FileContent buffer = myBuffer;
      if (buffer != null) {
        myBuffer = null;
        return buffer;
      }

      return super.take();
    }

    public void pushback(@NotNull FileContent content) {
      myBuffer = content;
    }

  }
}
