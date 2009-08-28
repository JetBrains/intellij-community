package com.intellij.ide.errorTreeView;

import com.intellij.openapi.vfs.VirtualFile;

public class SimpleErrorData {
  private final ErrorTreeElementKind myKind;
  private final String[] myMessages;
  private final VirtualFile myVf;

  public SimpleErrorData(ErrorTreeElementKind kind, String[] messages, VirtualFile vf) {
    myKind = kind;
    myMessages = messages;
    myVf = vf;
  }

  public ErrorTreeElementKind getKind() {
    return myKind;
  }

  public String[] getMessages() {
    return myMessages;
  }

  public VirtualFile getVf() {
    return myVf;
  }
}
