// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
public interface TypingActionsExtension {
  ExtensionPointName<TypingActionsExtension> EP_NAME = ExtensionPointName.create("com.intellij.typingActionsExtension");

  static @NotNull TypingActionsExtension findForContext(@NotNull Project project, @NotNull Editor editor) {
    final TypingActionsExtension extension =
      ContainerUtil.find(EP_NAME.getExtensionList(), provider -> provider.isSuitableContext(project, editor));
    return extension == null
      ? new DefaultTypingActionsExtension()
      : extension;
  }

  /**
   * Returns `true` if optimizes copy/paste procedure in the editor.
   *
   * @param project       current project
   * @param editor        target editor
   */
  boolean isSuitableContext(@NotNull Project project, @NotNull Editor editor);

  /**
   * Optimal implementation of formatting procedure in UI thread.
   * @param project       current project
   * @param editor        target editor
   * @param howtoReformat one of magic constants
   *                      {@code NO_REFORMAT, INDENT_BLOCK, INDENT_EACH_LINE, REFORMAT_BLOCK} from the {@code CodeInsightSettings} class
   * @param startOffset   the start offset of fragment
   * @param endOffset     the end offset of fragment
   * @param anchorColumn  the indent for the first line (with {@code INDENT_BLOCK}, {@code 0} with other consts)
   * @param indentationBeforeReformat indent block before re-format block (with {@code REFORMAT_BLOCK}, {@code false} with other consts)
   */
  default void format(@NotNull Project project,
                      @NotNull Editor editor,
                      int howtoReformat,
                      int startOffset,
                      int endOffset,
                      int anchorColumn,
                      boolean indentationBeforeReformat,
                      boolean formatInjected) {}

  /**
   * Entry point for implementing formatting and folding hints before pasting the text.
   *
   * @param project current project
   * @param editor  target editor
   */
  default void startPaste(@NotNull Project project, @NotNull Editor editor) {}

  /**
   * The entry point for postponed post-paste operations.
   *
   * @param project current project
   * @param editor  target editor
   */
  default void endPaste(@NotNull Project project, @NotNull Editor editor) {}

  /**
   * Entry point for document commit and other hints before copying the text.
   *
   * @param project current project
   * @param editor  target editor
   */
  default void startCopy(@NotNull Project project, @NotNull Editor editor) {}

  /**
   * The entry point for postponed post-copy operations.
   *
   * @param project current project
   * @param editor  target editor
   */
  default void endCopy(@NotNull Project project, @NotNull Editor editor) {}
}