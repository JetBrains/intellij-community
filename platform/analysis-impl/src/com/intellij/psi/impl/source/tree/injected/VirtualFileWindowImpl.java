// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

class VirtualFileWindowImpl extends LightVirtualFile implements VirtualFileWindow {
  private static final @NotNull Logger LOG = Logger.getInstance(VirtualFileWindowImpl.class);

  private final VirtualFile myDelegate;
  private final DocumentWindowImpl myDocumentWindow;

  VirtualFileWindowImpl(@NotNull String name,
                        @NotNull VirtualFile delegate,
                        @NotNull DocumentWindowImpl window,
                        @NotNull Language language,
                        @NotNull CharSequence text) {
    super(name, language, text);
    setCharset(delegate.getCharset(), null, false);
    setFileType(language.getAssociatedFileType());
    if (delegate instanceof VirtualFileWindow) throw new IllegalArgumentException(delegate +" must not be injected");
    myDelegate = delegate;
    myDocumentWindow = window;
  }

  @Override
  public @NotNull VirtualFile getDelegate() {
    return myDelegate;
  }

  @Override
  public @NotNull DocumentWindowImpl getDocumentWindow() {
    return myDocumentWindow;
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid() && myDocumentWindow.isValid();
  }

  @Override
  public String toString() {
    return "VirtualFileWindow in " + myDelegate.getPresentableUrl();
  }

  @Override
  public boolean isWritable() {
    return myDelegate.isWritable();
  }

  @Override
  public void setWritable(boolean writable) {
    LOG.error("Operation is not supported");
  }
}