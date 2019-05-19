// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spi;

import com.intellij.icons.AllIcons;
import com.intellij.lang.spi.SPILanguage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SPIFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
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
        for (Object condition : Extensions.getRootArea().getExtensionPoint("com.intellij.vetoSPICondition").getExtensions()) {
          if (((Condition<String>)condition).value(fileName)) return false;
        }
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
    return "Service Provider Interface";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return CharsetToolkit.UTF8;
  }
}
