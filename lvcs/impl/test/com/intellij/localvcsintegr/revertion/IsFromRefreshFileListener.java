package com.intellij.localvcsintegr.revertion;

import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;

public class IsFromRefreshFileListener extends VirtualFileAdapter {
  private String myLog = "";

  @Override
  public void fileCreated(VirtualFileEvent e) {
    log(e.getFile().isDirectory() ? "createDir" : "createFile", e);
  }

  @Override
  public void fileDeleted(VirtualFileEvent e) {
    log("delete", e);
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent e) {
    log("move", e);
  }

  @Override
  public void contentsChanged(VirtualFileEvent e) {
    log("content", e);
  }

  @Override
  public void propertyChanged(VirtualFilePropertyEvent e) {
    log("rename", e);
  }

  private void log(String s, VirtualFileEvent e) {
    myLog += s + " " + e.isFromRefresh() + " ";
  }

  public String getLog() {
    return myLog;
  }
}
