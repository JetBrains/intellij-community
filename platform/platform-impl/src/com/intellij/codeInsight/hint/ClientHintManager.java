// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientKind;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.JComponent;
import java.awt.Point;
import java.util.List;

/**
 * Interface to create hints for particular clients. Take a look at {@link com.intellij.openapi.client.ClientSession}
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface ClientHintManager {

  static @NotNull ClientHintManager getCurrentInstance() {
    return ApplicationManager.getApplication().getService(ClientHintManager.class);
  }

  static @NotNull @Unmodifiable List<ClientHintManager> getAllInstances() {
    return ApplicationManager.getApplication().getServices(ClientHintManager.class, ClientKind.ALL);
  }

  boolean canShowQuestionAction(QuestionAction action);

  void setRequestFocusForNextHint(boolean requestFocus);

  boolean performCurrentQuestionAction();

  boolean hasShownHintsThatWillHideByOtherHint(boolean willShowTooltip);

  void showGutterHint(final @NotNull LightweightHint hint,
                      final @NotNull Editor editor,
                      @NotNull HintHint hintInfo,
                      final int lineNumber,
                      final int horizontalOffset,
                      @HintManager.HideFlags final int flags,
                      final int timeout,
                      final boolean reviveOnEditorChange,
                      @Nullable Runnable onHintHidden);

  void showEditorHint(@NotNull LightweightHint hint,
                      @NotNull Editor editor,
                      @NotNull HintHint hintInfo,
                      @NotNull Point p,
                      @HintManager.HideFlags int flags,
                      int timeout,
                      boolean reviveOnEditorChange,
                      @Nullable Runnable onHintHidden);

  void showHint(@NotNull JComponent component, @NotNull RelativePoint p, int flags, int timeout, @Nullable Runnable onHintHidden);

  void hideAllHints();

  void cleanup();

  Point getHintPosition(@NotNull LightweightHint hint, @NotNull Editor editor, @HintManager.PositionFlags short constraint);

  /**
   * Displays a question hint at a specified position within the editor.
   * Highlights a segment of code between offset1 and offset2 if they are not equivalent.
   *
   * @param editor               The editor instance where the hint should be displayed.
   * @param offset1              The start offset in the editor where the hint is anchored.
   * @param offset2              The end offset in the editor where the hint is anchored.
   * @param attributesOverride   The text attributes override to be applied to a segment of code between offset1 and offset2.
   *                             Null if default underlining attributes should be used.
   * @param hint                 The hint to be displayed.
   * @param action               The action to be executed when a user interacts with the hint.
   * @param constraint           A positional constraint that defines where the hint should be displayed relative to the anchored offsets.
   */
  void showQuestionHint(@NotNull Editor editor,
                        @NotNull Point p,
                        int offset1,
                        int offset2,
                        @Nullable TextAttributes attributesOverride,
                        @NotNull LightweightHint hint,
                        int flags,
                        @NotNull QuestionAction action,
                        @HintManager.PositionFlags short constraint);

  boolean isEscapeHandlerEnabled();

  boolean hideHints(int mask, boolean onlyOne, boolean editorChanged);

  void onProjectClosed(@NotNull Project project);
}
