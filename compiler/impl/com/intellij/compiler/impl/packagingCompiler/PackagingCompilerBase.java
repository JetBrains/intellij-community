package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.compiler.impl.CompileDriver;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.deployment.DeploymentUtilImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * @author nik
 */
public abstract class PackagingCompilerBase<C extends ProcessingItemsBuilderContext> implements PackagingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.packagingCompiler.PackagingCompilerBase");
  private final Key<List<String>> myFilesToDeleteKey;
  private final Key<C> myBuilderContextKey;
  @Nullable private PackagingCompilerCache myOutputItemsCache;

  protected PackagingCompilerBase(final Key<List<String>> filesToDeleteKey, final Key<C> builderContextKey) {
    myFilesToDeleteKey = filesToDeleteKey;
    myBuilderContextKey = builderContextKey;
  }

  @NotNull
  private PackagingCompilerCache getOutputItemsCache(final Project project) {
    if (myOutputItemsCache == null) {
      myOutputItemsCache = new PackagingCompilerCache(CompilerPaths.getCompilerSystemDirectory(project).getPath() + File.separator +
                                                      getOutputCacheId() + "_timestamp.dat");
    }
    return myOutputItemsCache;
  }

  protected abstract String getOutputCacheId();

  protected abstract PackagingProcessingItem[] collectItems(C builderContext, final Project project);

  public void processOutdatedItem(final CompileContext context, final String url, @Nullable final ValidityState state) {
  }

  protected boolean collectFilesToDelete(final CompileContext context, final PackagingProcessingItem[] allProcessingItems) {
    List<String> filesToDelete = new ArrayList<String>();
    Set<String> outputPaths = createPathsHashSet();
    for (PackagingProcessingItem item : allProcessingItems) {
      for (DestinationInfo destinationInfo : item.getDestinations()) {
        String outputPath = getOutputPathWithJarSeparator(destinationInfo);
        outputPaths.add(outputPath);
      }
    }

    final Iterator<String> pathIterator = getOutputItemsCache(context.getProject()).getUrlsIterator();
    while (pathIterator.hasNext()) {
      String path = pathIterator.next();
      if (!outputPaths.contains(path)) {
        filesToDelete.add(path);
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Files to delete (" + filesToDelete.size() + "):");
      for (String path : filesToDelete) {
        LOG.debug(path);
      }
    }

    if (filesToDelete.isEmpty()) {
      return false;
    }
    context.putUserData(myFilesToDeleteKey, filesToDelete);
    return true;
  }

  private static String getOutputPathWithJarSeparator(DestinationInfo destinationInfo) {
    String outputPath = destinationInfo.getOutputFilePath();
    if (destinationInfo instanceof JarDestinationInfo) {
      final String fullOutputPath = destinationInfo.getOutputPath();
      final String fileOutputPath = destinationInfo.getOutputFilePath();
      if (fullOutputPath.startsWith(fileOutputPath)) {
        outputPath += JarFileSystem.JAR_SEPARATOR + DeploymentUtil.trimForwardSlashes(fullOutputPath.substring(fileOutputPath.length()));
      }
    }
    return outputPath;
  }

  private static THashSet<String> createPathsHashSet() {
    return SystemInfo.isFileSystemCaseSensitive ? new THashSet<String>() : new THashSet<String>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    final Set<String> deletedJars = deleteOutdatedFiles(context);

    final List<PackagingProcessingItem> processedItems = new ArrayList<PackagingProcessingItem>();
    final Set<String> writtenPaths = createPathsHashSet();
    final Ref<Boolean> built = Ref.create(false);
    CompileDriver.runInContext(context, "Copying files", new ThrowableRunnable<RuntimeException>() {
      public void run() throws RuntimeException {
        built.set(doBuild(context, items, processedItems, writtenPaths, deletedJars));
      }
    });
    if (!built.get()) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    context.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.updating.caches"));
    context.getProgressIndicator().setText2("");
    refreshOutputFiles(writtenPaths);
    new ReadAction() {
      protected void run(final Result result) {
        processDestinations(processedItems);
      }
    }.execute();
    removeInvalidItems(processedItems);
    updateOutputCache(context.getProject(), processedItems);
    return processedItems.toArray(new ProcessingItem[processedItems.size()]);
  }

  private static void removeInvalidItems(List<PackagingProcessingItem> processedItems) {
    Set<VirtualFile> files = new THashSet<VirtualFile>(processedItems.size());
    for (PackagingProcessingItem item : processedItems) {
      files.add(item.getFile());
    }
    RefreshQueue.getInstance().refresh(false, false, null, files.toArray(new VirtualFile[files.size()]));

    final Iterator<PackagingProcessingItem> iterator = processedItems.iterator();
    while (iterator.hasNext()) {
      PackagingProcessingItem item = iterator.next();
      final VirtualFile file = item.getFile();
      if (!file.isValid()) {
        iterator.remove();
      }
    }
  }

  private boolean doBuild(final CompileContext context, final ProcessingItem[] items, final List<PackagingProcessingItem> processedItems,
                                 final Set<String> writtenPaths, final Set<String> deletedJars) {
    if (LOG.isDebugEnabled()) {
      int num = Math.min(100, items.length);
      LOG.debug("Files to process (" + num + " of " + items.length + "):");
      for (int i = 0; i < num; i++) {
        LOG.debug(items[i].getFile().getPath());
      }
    }
    final DeploymentUtil deploymentUtil = DeploymentUtil.getInstance();
    final FileFilter fileFilter = new IgnoredFileFilter();
    final C builderContext = context.getUserData(myBuilderContextKey);
    final Set<JarInfo> changedJars = new THashSet<JarInfo>();
    for (String deletedJar : deletedJars) {
      final Collection<JarInfo> infos = builderContext.getJarInfos(deletedJar);
      if (infos != null) {
        changedJars.addAll(infos);
      }
    }
    beforeBuildStarted(builderContext);

    try {
      int i = 0;
      for (final ProcessingItem item0 : items) {
        if (item0 instanceof MockProcessingItem) continue;
        final PackagingProcessingItem item = (PackagingProcessingItem)item0;
        context.getProgressIndicator().checkCanceled();

        final Ref<IOException> exception = Ref.create(null);
        new ReadAction() {
          protected void run(final Result result) {
            final List<DestinationInfo> destinations = item.getDestinations();
            final File fromFile = VfsUtil.virtualToIoFile(item.getFile());
            for (DestinationInfo destination : destinations) {
              if (destination instanceof ExplodedDestinationInfo) {
                final ExplodedDestinationInfo explodedDestination = (ExplodedDestinationInfo)destination;
                File toFile = new File(FileUtil.toSystemDependentName(explodedDestination.getOutputPath()));
                if (fromFile.exists()) {
                  try {
                    deploymentUtil.copyFile(fromFile, toFile, context, writtenPaths, fileFilter);
                  }
                  catch (IOException e) {
                    exception.set(e);
                    return;
                  }
                }
                onFileCopied(builderContext, explodedDestination);
              }
              else {
                changedJars.add(((JarDestinationInfo)destination).getJarInfo());
              }
            }
          }
        }.execute();
        if (exception.get() != null) {
          throw exception.get();
        }

        context.getProgressIndicator().setFraction(++i*1.0/items.length);
        processedItems.add(item);
      }

      createManifestFiles(builderContext.getManifestFiles());

      JarsBuilder builder = new JarsBuilder(changedJars, fileFilter, context);
      final boolean processed = builder.buildJars(writtenPaths);
      if (!processed) {
        return false;
      }

      Set<VirtualFile> recompiledSources = new HashSet<VirtualFile>();
      for (JarInfo info : builder.getJarsToBuild()) {
        for (Pair<String, VirtualFile> pair : info.getPackedFiles()) {
          recompiledSources.add(pair.getSecond());
        }
      }
      for (PackagingProcessingItem processedItem : processedItems) {
        recompiledSources.remove(processedItem.getFile());
      }
      for (VirtualFile source : recompiledSources) {
        PackagingProcessingItem item = builderContext.getItemBySource(source);
        LOG.assertTrue(item != null, source);
        processedItems.add(item);
      }

      onBuildFinished(builderContext, builder, context.getProject());
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.info(e);
      context.addMessage(CompilerMessageCategory.ERROR, e.getLocalizedMessage(), null, -1, -1);
      return false;
    }
    return true;
  }

  protected void onBuildFinished(C context, JarsBuilder builder, final Project project) throws Exception {
  }

  private static void createManifestFiles(final List<ManifestFileInfo> manifestFileInfos) throws IOException {
    for (ManifestFileInfo manifestFileInfo : manifestFileInfos) {
      File outputFile = new File(FileUtil.toSystemDependentName(manifestFileInfo.getOutputPath()));
      Manifest manifest;
      if (outputFile.exists()) {
        FileInputStream stream = new FileInputStream(outputFile);
        try {
          manifest = new Manifest(stream);
        }
        finally {
          stream.close();
        }
      }
      else {
        manifest = new Manifest();
      }

      DeploymentUtilImpl.setManifestAttributes(manifest.getMainAttributes(), manifestFileInfo.getClasspath());

      FileUtil.createParentDirs(outputFile);
      FileOutputStream out = null;
      try {
        out = new FileOutputStream(outputFile);
        manifest.write(out);
      }
      finally {
        if (out != null) {
          out.close();
        }
      }
    }
  }

  private static void processDestinations(final List<PackagingProcessingItem> items) {
    for (PackagingProcessingItem item : items) {
      for (DestinationInfo destination : item.getDestinations()) {
        destination.update();
      }
    }
  }

  private Set<String> deleteOutdatedFiles(final CompileContext context) {
    context.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.deleting.outdated.files"));
    final List<String> filesToDelete = context.getUserData(myFilesToDeleteKey);
    final Set<String> deletedJars;
    if (filesToDelete != null) {
      deletedJars = deleteFiles(context.getProject(), filesToDelete);
    }
    else {
      deletedJars = Collections.emptySet();
    }
    context.getProgressIndicator().checkCanceled();
    return deletedJars;
  }

  protected Set<String> deleteFiles(final Project project, final List<String> paths) {
    final THashSet<String> deletedJars = new THashSet<String>();
    LOG.debug("Deleting outdated files...");
    List<File> filesToRefresh = new ArrayList<File>();
    for (String fullPath : paths) {
      int end = fullPath.indexOf(JarFileSystem.JAR_SEPARATOR);
      String filePath = end != -1 ? fullPath.substring(0, end) : fullPath;
      if (end != -1) {
        deletedJars.add(filePath);
      }
      File file = new File(FileUtil.toSystemDependentName(filePath));
      filesToRefresh.add(file);
      boolean deleted = FileUtil.delete(file);
      if (!deleted && LOG.isDebugEnabled()) {
        LOG.debug("Cannot delete file " + file);
      }

      if (deleted) {
        getOutputItemsCache(project).remove(fullPath);
      }
    }

    CompilerUtil.refreshIOFiles(filesToRefresh);
    return deletedJars;
  }

  private void updateOutputCache(final Project project, final List<PackagingProcessingItem> processedItems) {
    for (PackagingProcessingItem processedItem : processedItems) {
      for (DestinationInfo destinationInfo : processedItem.getDestinations()) {
        final VirtualFile virtualFile = destinationInfo.getOutputFile();
        if (virtualFile != null) {
          final String path = getOutputPathWithJarSeparator(destinationInfo);
          if (LOG.isDebugEnabled()) {
            LOG.debug("update output cache: file " + path);
          }
          getOutputItemsCache(project).update(path, virtualFile.getTimeStamp());
        }
      }
    }
    saveCacheIfDirty(project);
  }

  private static void refreshOutputFiles(Set<String> writtenPaths) {
    final ArrayList<File> filesToRefresh = new ArrayList<File>();
    for (String path : writtenPaths) {
      filesToRefresh.add(new File(path));
    }
    CompilerUtil.refreshIOFiles(filesToRefresh);
  }

  private void saveCacheIfDirty(final Project project) {
    if (getOutputItemsCache(project).isDirty()) {
      getOutputItemsCache(project).save();
    }
  }

  public ValidityState createValidityState(final DataInput is) throws IOException {
    return new PackagingItemValidityState(is);
  }

  public boolean validateConfiguration(final CompileScope scope) {
    return true;
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    return new ReadAction<ProcessingItem[]>() {
      protected void run(final Result<ProcessingItem[]> result) {
        final boolean shouldStartBuild = doNotStartBuild(context);
        if (shouldStartBuild) {
          result.setResult(ProcessingItem.EMPTY_ARRAY);
          return;
        }

        C builderContext = createContext(context);
        context.putUserData(myBuilderContextKey, builderContext);
        PackagingProcessingItem[] allProcessingItems = collectItems(builderContext, context.getProject());

        if (LOG.isDebugEnabled()) {
          int num = Math.min(100, allProcessingItems.length);
          LOG.debug("All files (" + num + " of " + allProcessingItems.length + "):");
          for (int i = 0; i < num; i++) {
              LOG.debug(allProcessingItems[i].getFile().getPath());
          }
        }

        boolean hasFilesToDelete = collectFilesToDelete(context, builderContext.getProcessingItems());
        if (hasFilesToDelete) {
          MockProcessingItem mockItem = new MockProcessingItem(new LightVirtualFile("239239293"));
          result.setResult(ArrayUtil.append(allProcessingItems, mockItem, ProcessingItem.class));
        }
        else {
          result.setResult(allProcessingItems);
        }
      }
    }.execute().getResultObject();
  }

  protected abstract boolean doNotStartBuild(CompileContext context);

  protected abstract C createContext(CompileContext context);

  protected void onFileCopied(C builderContext, ExplodedDestinationInfo explodedDestination) {
  }

  protected void beforeBuildStarted(C context) {
  }

  private static class MockProcessingItem implements ProcessingItem {
    private final VirtualFile myFile;

    public MockProcessingItem(final VirtualFile file) {
      myFile = file;
    }

    @NotNull
    public VirtualFile getFile() {
      return myFile;
    }

    @Nullable
    public ValidityState getValidityState() {
      return null;
    }
  }
}
