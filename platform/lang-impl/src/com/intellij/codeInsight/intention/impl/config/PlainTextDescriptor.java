// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.intention.impl.config;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class PlainTextDescriptor implements TextDescriptor {
  private final @Nls String myText;
  private final @NonNls String myFileName;

  public PlainTextDescriptor(@NotNull @Nls String text, @NotNull @NonNls String fileName) {
    myText = text;
    myFileName = fileName;
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public String getFileName() {
    return myFileName;
  }
}
