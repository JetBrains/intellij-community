/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.Collections;

public class VcsException extends Exception {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.VcsException");

  private VirtualFile myVirtualFile;
  private Collection myMessages;
  private boolean isWarning = false;

  public VcsException(String message) {
    super(message);
    String shownMessage = message == null ? "Unknown error" : message;
    myMessages = Collections.singleton(shownMessage);
  }

  public VcsException(Throwable throwable) {
    this(throwable.getMessage() != null ? throwable.getMessage() : throwable.getLocalizedMessage());
    LOG.info(throwable);
  }

  public VcsException(Collection messages) {
    myMessages = messages;
  }

  //todo: should be in constructor?
  public void setVirtualFile(VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public String[] getMessages() {
    return (String[])myMessages.toArray(new String[myMessages.size()]);
  }

  public VcsException setIsWarning(boolean warning) {
    isWarning = warning;
    return this;
  }

  public boolean isWarning() {
    return isWarning;
  }
}
