/*
 * Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
public interface CopyPasteExtension {
  ExtensionPointName<CopyPasteExtension> EP_NAME = ExtensionPointName.create("com.intellij.copyPasteExtension");

  @NotNull
  static CopyPasteExtension findForContext(@NotNull Project project, @NotNull Editor editor) {
    final CopyPasteExtension extension =
      ContainerUtil.find(EP_NAME.getExtensionList(), provider -> provider.isSuitableContext(project, editor));
    return extension == null
      ? new DefaultCopyPasteExtension()
      : extension;
  }

  /**
   * Returns `true` if optimizes copy/paste procedure in the editor.
   *
   * @param project       current project
   * @param editor        target editor
   * @return
   */
  boolean isSuitableContext(@NotNull Project project, @NotNull Editor editor);

  /**
   * Optimal implementation of formatting procedure after the Paste action.
   *
   * @param project       current project
   * @param editor        target editor
   * @param howtoReformat one of magic constants
   *                      {@code NO_REFORMAT, INDENT_BLOCK, INDENT_EACH_LINE, REFORMAT_BLOCK} from the {@code CodeInsightSettings} class
   * @param startOffset   the start offset of pasted fragment
   * @param endOffset     the end offset of pasted fragment
   * @param anchorColumn  the indent for the first pasted line
   */
  default void format(@NotNull Project project,
                      @NotNull Editor editor,
                      int howtoReformat,
                      int startOffset,
                      int endOffset,
                      int anchorColumn) {}

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