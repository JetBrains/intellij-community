/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CollectingContentIterator;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class FileBasedIndexProjectHandler extends AbstractProjectComponent {
  private final FileBasedIndex myIndex;
  private final ProjectRootManagerEx myRootManager;

  public FileBasedIndexProjectHandler(FileBasedIndex index, final Project project, final ProjectRootManagerEx rootManager) {
    super(project);
    myIndex = index;
    myRootManager = rootManager;

    final UnindexedFilesUpdater updater = new UnindexedFilesUpdater();
    final StartupManagerEx startupManager = (StartupManagerEx)StartupManager.getInstance(project);
    if (startupManager != null) {
      startupManager.registerPreStartupActivity(new Runnable() {
        public void run() {
          startupManager.getFileSystemSynchronizer().registerCacheUpdater(updater);
          rootManager.registerChangeUpdater(updater);
        }
      });
    }
  }

  private class UnindexedFilesUpdater implements CacheUpdater {
    public VirtualFile[] queryNeededFiles() {
      CollectingContentIterator finder = myIndex.createContentIterator();
      iterateIndexableFiles(finder);
      final List<VirtualFile> files = finder.getFiles();
      return files.toArray(new VirtualFile[files.size()]);
    }

    public void processFile(final FileContent fileContent) {
      myIndex.indexFileContent(fileContent);
    }

    private void iterateIndexableFiles(final ContentIterator processor) {
      // todo: iterate all files that can be indexed, not just content
      myRootManager.getFileIndex().iterateContent(processor);
    }


    public void updatingDone() {
    }

    public void canceled() {
    }
  }
}