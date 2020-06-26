// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.core.CoreBundle;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class PlainTextFileType extends LanguageFileType implements PlainTextLikeFileType {
  public static final PlainTextFileType INSTANCE = new PlainTextFileType();

  private PlainTextFileType() {
    super(PlainTextLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "PLAIN_TEXT";
  }

  @Override
  @NotNull
  public String getDescription() {
    return CoreBundle.message("filetype.plaintext.description");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "txt";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }
}
