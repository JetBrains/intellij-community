// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public String getName() {
    return "Mock";
  }

  @Override
  @NotNull
  public String getDescription() {
    //noinspection HardCodedStringLiteral
    return "Mock";
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return ".mockExtensionThatProbablyWon'tEverExist";
  }

  @Override
  public Icon getIcon() {
    return null;
  }
}
