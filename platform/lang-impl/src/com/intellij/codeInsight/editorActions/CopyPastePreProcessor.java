// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension allows modifying text that's copied from editor by 'Copy' action and text that's inserted into editor by 'Paste' action.
 */
public interface CopyPastePreProcessor {
  ExtensionPointName<CopyPastePreProcessor> EP_NAME = ExtensionPointName.create("com.intellij.copyPastePreProcessor");

  /**
   * If not-null value is returned by this method, it will replace copied text. No other preprocessor will be invoked at copy time after this.
   */
  @Nullable
  String preprocessOnCopy(final PsiFile file, final int[] startOffsets, final int[] endOffsets, String text);

  /**
   * Replaces pasted text. {@code text} value should be returned if no processing is required.
   */
  @NotNull
  String preprocessOnPaste(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText);

  //For performance optimization implementations can return false in case when they dont have access to any other documents(psi file)
  // except current one
  default boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
    return true;
  }
}
