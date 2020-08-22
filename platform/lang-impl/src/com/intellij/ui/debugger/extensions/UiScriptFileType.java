// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.debugger.extensions;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class UiScriptFileType implements FileType {
  private static UiScriptFileType myInstance;

  private UiScriptFileType() {
  }

  public static UiScriptFileType getInstance() {
    if (myInstance == null) {
      myInstance = new UiScriptFileType();
    }
    return myInstance;
  }

  @Override
  @NotNull
  public String getName() {
    return "UI Script";
  }

  @Override
  @NotNull
  public String getDescription() {
    return "UI test script";
  }

  public static final String myExtension = "ijs";

  @Override
  @NotNull
  public String getDefaultExtension() {
    return myExtension;
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    return CharsetToolkit.UTF8;
  }
}
