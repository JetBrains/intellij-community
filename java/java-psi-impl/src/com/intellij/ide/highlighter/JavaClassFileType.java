// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class JavaClassFileType implements FileType {

  public static final JavaClassFileType INSTANCE = new JavaClassFileType();

  private JavaClassFileType() {
  }

  @Override
  @NotNull
  public String getName() {
    return "CLASS";
  }

  @Override
  @NotNull
  public String getDescription() {
    return JavaPsiBundle.message("filetype.description.class");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "class";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.JavaClass;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    return null;
  }
}
