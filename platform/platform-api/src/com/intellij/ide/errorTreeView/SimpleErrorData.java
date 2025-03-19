// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.errorTreeView;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class SimpleErrorData {
  private final ErrorTreeElementKind myKind;
  private final String[] myMessages;
  private final VirtualFile myVf;

  public SimpleErrorData(@NotNull ErrorTreeElementKind kind, String[] messages, VirtualFile vf) {
    myKind = kind;
    myMessages = messages;
    myVf = vf;
  }

  public @NotNull ErrorTreeElementKind getKind() {
    return myKind;
  }

  public String[] getMessages() {
    return myMessages;
  }

  public VirtualFile getVf() {
    return myVf;
  }
}
