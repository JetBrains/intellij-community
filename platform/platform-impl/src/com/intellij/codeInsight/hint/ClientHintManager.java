// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface ClientHintManager {

  static @NotNull ClientHintManager getCurrentInstance() {
    return ApplicationManager.getApplication().getService(ClientHintManager.class);
  }

  boolean canShowQuestionAction(QuestionAction action);

  void setRequestFocusForNextHint(boolean requestFocus);

  boolean performCurrentQuestionAction();

  boolean hasShownHintsThatWillHideByOtherHint(boolean willShowTooltip);

  void showEditorHint(LightweightHint hint,
                      Editor editor,
                      @HintManager.PositionFlags short constraint,
                      @HintManager.HideFlags int flags,
                      int timeout,
                      boolean reviveOnEditorChange);

  void showEditorHint(@NotNull LightweightHint hint,
                      @NotNull Editor editor,
                      @NotNull Point p,
                      @HintManager.HideFlags int flags,
                      int timeout,
                      boolean reviveOnEditorChange,
                      HintHint hintInfo);

  void showHint(@NotNull JComponent component, @NotNull RelativePoint p, int flags, int timeout, @Nullable Runnable onHintHidden);

  void hideAllHints();

  void cleanup();

  Point getHintPosition(@NotNull LightweightHint hint, @NotNull Editor editor, @HintManager.PositionFlags short constraint);

  void showErrorHint(@NotNull Editor editor, @NotNull String text, short position);

  void showInformationHint(@NotNull Editor editor,
                           @NotNull JComponent component,
                           @HintManager.PositionFlags short position,
                           @Nullable Runnable onHintHidden);

  void showQuestionHint(@NotNull Editor editor,
                        @NotNull Point p,
                        int offset1,
                        int offset2,
                        @NotNull LightweightHint hint,
                        @NotNull QuestionAction action,
                        @HintManager.PositionFlags short constraint);

  boolean isEscapeHandlerEnabled();

  boolean hideHints(int mask, boolean onlyOne, boolean editorChanged);
}
