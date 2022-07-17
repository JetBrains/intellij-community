// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

/**
 * Handlers to be used in: "Go to Super", "Override Methods...", "Implement Methods...".
 * {@link CodeInsightActions} declares corresponding EPs to register such language handlers. 
 * 
 * @see com.intellij.codeInsight.generation.actions.PresentableActionHandlerBasedAction
 */
public interface LanguageCodeInsightActionHandler extends CodeInsightActionHandler {

  /**
   * Checks whether handler should process {@code file} or it should give a chance to the next handler.
   * @param editor  the editor where action is invoked.
   * @param file    the file open in the editor.
   * @return {@code true} if handler should process file, {@code false} otherwise.
   */
  boolean isValidFor(Editor editor, PsiFile file);
}
