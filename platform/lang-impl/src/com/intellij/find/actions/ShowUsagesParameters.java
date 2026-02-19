// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IntRef;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.find.actions.ShowUsagesAction.getUsagesPageSize;

public final class ShowUsagesParameters {

  final @NotNull Project project;
  final @Nullable Editor editor;
  final @NotNull RelativePoint popupPosition;
  final @NotNull IntRef minWidth;
  final int maxUsages;

  public ShowUsagesParameters(@NotNull Project project,
                              @Nullable Editor editor,
                              @NotNull RelativePoint popupPosition,
                              @NotNull IntRef minWidth,
                              int maxUsages) {
    this.project = project;
    this.editor = editor;
    this.popupPosition = popupPosition;
    this.minWidth = minWidth;
    this.maxUsages = maxUsages;
  }

  public @Nullable Editor getEditor() {
    return editor;
  }

  public int getMaxUsages() {
    return maxUsages;
  }

  public @NotNull RelativePoint getPopupPosition() {
    return popupPosition;
  }

  public @NotNull ShowUsagesParameters moreUsages() {
    return new ShowUsagesParameters(project, editor, popupPosition, minWidth, maxUsages + getUsagesPageSize());
  }

  public @NotNull ShowUsagesParameters withUsages(int maxUsages) {
    return new ShowUsagesParameters(project, editor, popupPosition, minWidth, maxUsages);
  }

  public @NotNull ShowUsagesParameters withEditor(@NotNull Editor editor) {
    return new ShowUsagesParameters(project, editor, popupPosition, minWidth, maxUsages);
  }

  public static @NotNull ShowUsagesParameters initial(@NotNull Project project, @Nullable Editor editor, @NotNull RelativePoint popupPosition) {
    return new ShowUsagesParameters(project, editor, popupPosition, new IntRef(0), getUsagesPageSize());
  }
}
