package com.intellij.openapi.samples;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;

public class VfsSamplePlugin implements ProjectComponent {
  private Project myProject;
  private MyVfsListener myVfsListener;

  private static int ourJavaFilesCount;

  public VfsSamplePlugin(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
    VirtualFile[] sourceRoots = projectRootManager.getRootFiles(ProjectRootType.SOURCE);

    ourJavaFilesCount = 0;

    for (int i = 0; i < sourceRoots.length; i++) {
      VirtualFile sourceRoot = sourceRoots[i];
      countJavaFiles(sourceRoot);
    }

    myVfsListener = new MyVfsListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myVfsListener);
  }

  public void projectClosed() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myVfsListener);
  }

  public void initComponent() {
    // empty
  }

  public void disposeComponent() {
    // empty
  }

  public String getComponentName() {
    return "VfsSample.VfsSamplePlugin";
  }

  private void updateCount(VirtualFile file, int increase) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (!fileTypeManager.isFileIgnored(file.getName())
      && fileTypeManager.getFileTypeByFile(file) == StdFileTypes.JAVA) {
      ourJavaFilesCount += increase;
      System.out.println("ourJavaFilesCount = " + ourJavaFilesCount);
    }
  }

  private void countJavaFiles(VirtualFile virtualFile) {
    VirtualFile[] children = virtualFile.getChildren();
    if (children == null) return;
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      updateCount(child, +1);
      countJavaFiles(child);
    }
  }

  // -------------------------------------------------------------------------
  // MyVfsListener
  // -------------------------------------------------------------------------

  private class MyVfsListener extends VirtualFileAdapter {
    public void fileCreated(VirtualFileEvent event) {
      updateCount(event.getFile(), +1);
    }

    public void fileDeleted(VirtualFileEvent event) {
      updateCount(event.getFile(), -1);
    }
  }
}
