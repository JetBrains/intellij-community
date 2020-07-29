// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

final class SymbolPresentationImpl implements SymbolPresentation {

  private final @Nullable Icon myIcon;
  private final @NotNull String myShortName;
  private final @NotNull String myShortDescription;
  private final @NotNull String myLongDescription;

  SymbolPresentationImpl(@Nullable Icon icon,
                         @NotNull String shortNameString,
                         @NotNull String shortDescription,
                         @NotNull String longDescription) {
    myIcon = icon;
    myShortName = shortNameString;
    myShortDescription = shortDescription;
    myLongDescription = longDescription;
  }

  @Override
  public @Nullable Icon getIcon() {
    return myIcon;
  }

  @Override
  public @Nls @NotNull String getShortNameString() {
    return myShortName;
  }

  @Override
  public @Nls(capitalization = Sentence) @NotNull String getShortDescription() {
    return myShortDescription;
  }

  @Override
  public @Nls(capitalization = Sentence) @NotNull String getLongDescription() {
    return myLongDescription;
  }
}
