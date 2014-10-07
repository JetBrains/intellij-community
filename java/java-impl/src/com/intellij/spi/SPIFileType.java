/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * User: anna
 */
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
        for (Object condition : Extensions.getExtensions("com.intellij.vetoSPICondition")) {
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

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return CharsetToolkit.UTF8;
  }
}
