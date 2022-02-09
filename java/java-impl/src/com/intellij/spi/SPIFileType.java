// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spi;

import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.lang.spi.SPILanguage;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class SPIFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
  public static final SPIFileType INSTANCE = new SPIFileType();

  private SPIFileType() {
    super(SPILanguage.INSTANCE);
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    if (parent != null && Comparing.equal("services", parent.getNameSequence())) {
      final VirtualFile gParent = parent.getParent();
      if (gParent != null && Comparing.equal("META-INF", gParent.getNameSequence())) {
        final String fileName = file.getName();
        return FileTypeRegistry.getInstance().getFileTypeByFileName(fileName) == FileTypes.UNKNOWN;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public String getName() {
    return "SPI";
  }

  @NotNull
  @Override
  public String getDescription() {
    return JavaBundle.message("filetype.spi.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    return CharsetToolkit.UTF8;
  }
}
