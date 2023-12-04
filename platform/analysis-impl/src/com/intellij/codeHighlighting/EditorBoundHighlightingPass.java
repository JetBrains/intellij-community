// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeHighlighting;

import com.intellij.codeInsight.highlighting.PassRunningAssert;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * The pass which should be applied to every editor, even if there are many for this document.
 *
 * Ordinary {@link TextEditorHighlightingPass} is document-bound,
 * i.e. after the pass finishes the markup is stored in the document.
 * For example, there is no point to recalculate syntax errors for each splitted editor of the same document.
 * This pass however is for editor-specific markup, e.g. code folding.
 */
public abstract class EditorBoundHighlightingPass extends TextEditorHighlightingPass {
  protected final @NotNull Editor myEditor;
  protected final @NotNull PsiFile myFile;

  private static final PassRunningAssert HIGHLIGHTING_PERFORMANCE_ASSERT =
    new PassRunningAssert("the expensive method should not be called inside the highlighting pass");

  protected static AccessToken runPass() {
    return HIGHLIGHTING_PERFORMANCE_ASSERT.runPass();
  }

  public static void assertHighlightingPassNotRunning() {
    HIGHLIGHTING_PERFORMANCE_ASSERT.assertPassNotRunning();
  }

  protected EditorBoundHighlightingPass(@NotNull Editor editor,
                                        @NotNull PsiFile psiFile,
                                        boolean runIntentionPassAfter) {
    super(psiFile.getProject(), editor.getDocument(), runIntentionPassAfter);
    myEditor = editor;
    myFile = psiFile;
  }
}
