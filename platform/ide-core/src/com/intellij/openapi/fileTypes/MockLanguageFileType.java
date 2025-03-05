// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class MockLanguageFileType extends LanguageFileType {
  public static final LanguageFileType INSTANCE = new MockLanguageFileType();

  private MockLanguageFileType() {
    super(Language.ANY);
  }

  @Override
  public @NotNull String getName() {
    return "Mock";
  }

  @Override
  public @NotNull String getDescription() {
    //noinspection HardCodedStringLiteral
    return "Mock";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return ".mockExtensionThatProbablyWon'tEverExist";
  }

  @Override
  public Icon getIcon() {
    return null;
  }
}
