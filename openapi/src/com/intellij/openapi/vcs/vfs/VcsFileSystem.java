package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.application.ApplicationManager;


public class VcsFileSystem extends VirtualFileSystem implements ApplicationComponent {

  private final String myProtocol;

  public static VcsFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(VcsFileSystem.class);
  }

  public VcsFileSystem() {
    myProtocol = "vcs";
  }

  public String getProtocol() {
    return myProtocol;
  }

  public VirtualFile findFileByPath(String path) {
    return null;
  }

  public void refresh(boolean asynchronous) {
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return null;
  }

  public void fireContentsChanged(Object requestor, VirtualFile file, long oldModificationStamp) {
    super.fireContentsChanged(requestor, file, oldModificationStamp);
  }

  protected void fireBeforeFileDeletion(Object requestor, VirtualFile file) {
    super.fireBeforeFileDeletion(requestor, file);
  }

  protected void fireFileDeleted(Object requestor,
                                 VirtualFile file,
                                 String fileName,
                                 boolean isDirectory,
                                 VirtualFile parent) {
    super.fireFileDeleted(requestor, file, fileName, isDirectory, parent);
  }

  public String getComponentName() {
    return "VcsFileSystem";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  protected void fireBeforeContentsChange(Object requestor, VirtualFile file) {
    super.fireBeforeContentsChange(requestor, file);
  }
}
