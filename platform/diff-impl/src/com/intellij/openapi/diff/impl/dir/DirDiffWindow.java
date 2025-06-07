// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public interface DirDiffWindow {
  @NotNull
  Disposable getDisposable();

  void setTitle(@NotNull @NlsContexts.DialogTitle String title);


  class Dialog implements DirDiffWindow {
    private final @NotNull DirDiffDialog myDialog;

    public Dialog(@NotNull DirDiffDialog dialog) {
      myDialog = dialog;
    }

    @Override
    public @NotNull Disposable getDisposable() {
      return myDialog.getDisposable();
    }

    @Override
    public void setTitle(@NotNull @NlsContexts.DialogTitle String title) {
      myDialog.setTitle(title);
    }
  }

  class Frame implements DirDiffWindow {
    private final @NotNull DirDiffFrame myFrame;

    public Frame(@NotNull DirDiffFrame frame) {
      myFrame = frame;
    }

    @Override
    public @NotNull Disposable getDisposable() {
      return myFrame;
    }

    @Override
    public void setTitle(@NotNull String title) {
      ((JFrame)myFrame.getFrame()).setTitle(title);
    }
  }
}
