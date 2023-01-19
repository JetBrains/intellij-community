// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.presentation;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

/**
 * @deprecated see {@link PresentableSymbol} deprecation notice
 */
@ScheduledForRemoval
@Deprecated
final class SymbolPresentationImpl implements SymbolPresentation {

  private final @Nullable Icon myIcon;
  private final @NotNull String myShortName;
  private final @NotNull @Nls(capitalization = Sentence) String myShortDescription;
  private final @NotNull @NlsContexts.DetailedDescription String myLongDescription;

  SymbolPresentationImpl(@Nullable Icon icon,
                         @NotNull @NlsSafe String shortNameString,
                         @NotNull @NlsContexts.DetailedDescription String shortDescription,
                         @NotNull @NlsContexts.DetailedDescription String longDescription) {
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
  public @NotNull String getShortNameString() {
    return myShortName;
  }

  @Override
  public @NotNull String getShortDescription() {
    return myShortDescription;
  }

  @Override
  public @NotNull String getLongDescription() {
    return myLongDescription;
  }
}
