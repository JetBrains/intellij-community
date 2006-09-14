/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.Collections;

public class VcsException extends Exception {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.VcsException");
  public static final VcsException[] EMPTY_ARRAY = new VcsException[0];

  private VirtualFile myVirtualFile;
  private Collection<String> myMessages;
  private boolean isWarning = false;

  public VcsException(String message) {
    super(message);
    String shownMessage = message == null ? VcsBundle.message("exception.text.unknown.error") : message;
    myMessages = Collections.singleton(shownMessage);
  }

  public VcsException(Throwable throwable) {
    this(throwable.getMessage() != null ? throwable.getMessage() : throwable.getLocalizedMessage());
    LOG.info(throwable);
  }

  public VcsException(Collection<String> messages) {
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
    return myMessages.toArray(new String[myMessages.size()]);
  }

  public VcsException setIsWarning(boolean warning) {
    isWarning = warning;
    return this;
  }

  public boolean isWarning() {
    return isWarning;
  }
}
