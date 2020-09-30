// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FileTemplateDescriptor {
  private final String myFileName;
  private final Icon myIcon;

  public FileTemplateDescriptor(@NotNull String fileName) {
    this(fileName, FileTypeRegistry.getInstance().getFileTypeByFileName(fileName).getIcon());
  }

  public FileTemplateDescriptor(@NotNull String fileName, @Nullable Icon icon) {
    myIcon = icon;
    myFileName = fileName;
  }

  public @NotNull @NlsSafe String getDisplayName() {
    return getFileName();
  }

  public @NotNull @NlsSafe String getFileName() {
    return myFileName;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }
}
