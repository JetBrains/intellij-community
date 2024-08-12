// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.debugger.extensions;

import com.intellij.lang.LangBundle;
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
  public @NotNull String getName() {
    return "UI Script";
  }

  @Override
  public @NotNull String getDescription() {
    return LangBundle.message("filetype.ui.script.description");
  }

  public static final String myExtension = "ijs";

  @Override
  public @NotNull String getDefaultExtension() {
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
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    return CharsetToolkit.UTF8;
  }
}
