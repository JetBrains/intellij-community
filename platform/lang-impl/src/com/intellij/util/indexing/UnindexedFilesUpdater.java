/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.indexing;

import com.intellij.ide.startup.BackgroundableCacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
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
    //for (Iterator<VirtualFile> virtualFileIterator = files.iterator(); virtualFileIterator.hasNext();) {
    //  VirtualFile file = virtualFileIterator.next();
    //  if (file.getUserData(DONT_INDEX_AGAIN_KEY) != null) {
    //    virtualFileIterator.remove();
    //  }
    //}
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
                                         "Sending indices update to background allows you to immediately use the most<br>" +
                                         "basic editing capabilities of " + ApplicationNamesInfo.getInstance().getFullProductName() + ", plus version control operations.<p>" +
                                         "However, many advanced functions such as 'Go to Class', advanced error highlighting,<br>" +
                                         "refactorings and some others <b>will not be available</b> until indexing is complete.<p>" +
                                         "Do you still want to send indexing to background?</html>", "Background Indexing",
                              new String[]{"Yes", "No"}, 1, UIUtil.getInformationIcon()) != 0) {
        return false;
      }
    }

    value.setValue("true");

    return true;
  }

  public void backgrounded(final Collection<VirtualFile> remaining) {
    new BackgroundCacheUpdaterRunner(myProject, remaining).processFiles(this);
  }

  public void processFile(final FileContent fileContent) {
    fileContent.putUserData(FileBasedIndex.PROJECT, myProject);
    myIndex.indexFileContent(fileContent);
    IndexingStamp.flushCache();
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
}
