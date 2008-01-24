/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class FileBasedIndexProjectHandler extends AbstractProjectComponent implements IndexableFileSet {
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
      final ProjectFileIndex projectFileIndex = myRootManager.getFileIndex();
      // iterate associated libraries
      final Module[] modules = ModuleManager.getInstance(myProject).getModules();
      for (Module module : modules) {
        OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
        for (OrderEntry orderEntry : orderEntries) {
          if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
            VirtualFile[] roots = orderEntry.getFiles(OrderRootType.SOURCES);
            for (VirtualFile root : roots) {
              iterateRecursively(root, processor);
            }
          }
        }
      }
      // iterate project content 
      projectFileIndex.iterateContent(processor);
    }

    private void iterateRecursively(final VirtualFile root, final ContentIterator processor) {
      for (VirtualFile file : root.getChildren()) {
        if (file.isDirectory()) {
          iterateRecursively(file, processor);
        }
        else {
          processor.processFile(file);
        }
      }
    }

    public void updatingDone() {
    }

    public void canceled() {
    }
  }
  
  public void disposeComponent() {
    myIndex.removeIndexableSet(this);
  }
}