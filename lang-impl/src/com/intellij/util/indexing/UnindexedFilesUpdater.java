package com.intellij.util.indexing;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;

import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
*         Date: Jan 29, 2008
*/
public class UnindexedFilesUpdater implements CacheUpdater {
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
    return files.toArray(new VirtualFile[files.size()]);
  }

  public void processFile(final FileContent fileContent) {
    myIndex.indexFileContent(fileContent);
  }

  private void iterateIndexableFiles(final ContentIterator processor) {
    final ProjectFileIndex projectFileIndex = myRootManager.getFileIndex();
    // iterate associated libraries
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    // iterate project content
    projectFileIndex.iterateContent(processor);

    Set<VirtualFile> visitedRoots = new HashSet<VirtualFile>();

    for (Module module : modules) {
      OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
          VirtualFile[] roots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (VirtualFile root : roots) {
            if (!visitedRoots.contains(root)) {
              visitedRoots.add(root);
              iterateRecursively(root, processor);
            }
          }
        }
      }
    }
  }

  private static void iterateRecursively(final VirtualFile root, final ContentIterator processor) {
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
