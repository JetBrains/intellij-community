// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public abstract class GutterMarkWrapper<T extends GutterIconRenderer> extends GutterIconRenderer implements MergeableGutterIconRenderer {

  protected final T originalMark;

  public T getOriginalMark() { return originalMark; }

  protected GutterMarkWrapper(T originalMark) {
    this.originalMark = originalMark;
  }

  @Override public @NotNull Icon getIcon() { return originalMark.getIcon(); }
  @Override public @Nullable String getTooltipText() { return originalMark.getTooltipText(); }
  @Override public @NotNull Alignment getAlignment() { return Alignment.LEFT; }
  @Override public @NotNull String getAccessibleName() { return originalMark.getAccessibleName(); }
  @Override public @Nullable String getAccessibleTooltipText() { return originalMark.getAccessibleTooltipText(); }
  @Override public @Nullable GutterDraggableObject getDraggableObject() { return originalMark.getDraggableObject(); }
  @Override public @Nullable AnAction getMiddleButtonClickAction() { return originalMark.getMiddleButtonClickAction(); }
  @Override public @Nullable ActionGroup getPopupMenuActions() { return originalMark.getPopupMenuActions(); }
  @Override public @Nullable AnAction getRightButtonClickAction() { return originalMark.getRightButtonClickAction(); }
  @Override public boolean isNavigateAction() { return originalMark.isNavigateAction(); }
  @Override public boolean isDumbAware() { return originalMark.isDumbAware(); }
  @Override public @NotNull String toString() { return originalMark.toString(); }

  @Override public abstract @Nullable AnAction getClickAction();
}
