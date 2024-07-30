// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class PlainTextDescriptor implements TextDescriptor {
  private final @Nls String myText;
  private final @NonNls String myFileName;

  public PlainTextDescriptor(@NotNull @Nls String text, @NotNull @NonNls String fileName) {
    myText = text;
    myFileName = fileName;
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }

  @Override
  public @NotNull String getFileName() {
    return myFileName;
  }
}
