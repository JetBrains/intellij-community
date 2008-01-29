/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;

public class FileBasedIndexProjectHandler extends AbstractProjectComponent implements IndexableFileSet {
  private final FileBasedIndex myIndex;
  private final ProjectRootManagerEx myRootManager;

  public FileBasedIndexProjectHandler(FileBasedIndex index, final Project project, final ProjectRootManagerEx rootManager) {
    super(project);
    myIndex = index;
    myRootManager = rootManager;

    final UnindexedFilesUpdater updater = new UnindexedFilesUpdater(project, rootManager, index);
    final StartupManagerEx startupManager = (StartupManagerEx)StartupManager.getInstance(project);
    if (startupManager != null) {
      startupManager.registerPreStartupActivity(new Runnable() {
        public void run() {
          startupManager.getFileSystemSynchronizer().registerCacheUpdater(updater);
          rootManager.registerChangeUpdater(updater);
          myIndex.registerIndexableSet(FileBasedIndexProjectHandler.this);
        }
      });
    }
  }

  public boolean isInSet(final VirtualFile file) {
    return myRootManager.getFileIndex().isInContent(file);
  }

  public void iterateIndexableFilesIn(final VirtualFile file, final ContentIterator iterator) {
    if (file.isDirectory()) {
      myRootManager.getFileIndex().iterateContentUnderDirectory(file, iterator);
    }
    else if (isInSet(file)) {
      iterator.processFile(file);
    }
  }

  public void disposeComponent() {
    myIndex.removeIndexableSet(this);
  }
}