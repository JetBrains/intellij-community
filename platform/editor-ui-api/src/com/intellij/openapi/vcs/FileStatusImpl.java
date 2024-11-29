// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.function.Supplier;

final class FileStatusImpl implements FileStatus {

  private final String myStatus;
  private final ColorKey myColorKey;
  private final Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> myTextSupplier;

  FileStatusImpl(@NotNull String status,
                 @NotNull ColorKey key,
                 @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> textSupplier) {
    myStatus = status;
    myColorKey = key;
    myTextSupplier = textSupplier;
  }

  @Override
  public @NonNls String toString() {
    return myStatus;
  }

  @Override
  public String getText() {
    return myTextSupplier.get();
  }

  @Override
  public Color getColor() {
    return EditorColorsManager.getInstance().getSchemeForCurrentUITheme().getColor(getColorKey());
  }

  @Override
  public @NotNull ColorKey getColorKey() {
    return myColorKey;
  }

  @Override
  public @NotNull String getId() {
    return myStatus;
  }
}
