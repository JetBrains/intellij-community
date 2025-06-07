// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.GutterMark;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

@ApiStatus.Internal
public abstract class MergeableGutterIconRendererExternalWrapper implements MergeableGutterIconRenderer {

  private final GutterMark myOriginalMark;

  public MergeableGutterIconRendererExternalWrapper(GutterMark originalMark) {
    myOriginalMark = originalMark;
  }

  public GutterMark getOriginalMark() { return myOriginalMark; }

  @Override
  public @NotNull Icon getIcon() { return myOriginalMark.getIcon(); }
  @Override
  public @Nullable String getTooltipText() { return myOriginalMark.getTooltipText(); }

  @Override
  public boolean equals(@Nullable Object other) {
    if (!(other instanceof MergeableGutterIconRendererExternalWrapper)) return false;
    return Objects.equals(myOriginalMark, ((MergeableGutterIconRendererExternalWrapper)other).myOriginalMark);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myOriginalMark);
  }
}
