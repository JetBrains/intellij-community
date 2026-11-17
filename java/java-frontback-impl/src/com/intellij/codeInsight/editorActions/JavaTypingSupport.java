// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.NewRdCompletionSupport;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * The part of {@link JavaTypedHandlerBase} that needs a resolve, a type or the backend completion.
 * The basic Java plugin registers this default, and the Java plugin overrides it.
 */
public class JavaTypingSupport {
  public static @NotNull JavaTypingSupport getInstance() {
    return ApplicationManager.getApplication().getService(JavaTypingSupport.class);
  }

  /**
   * Automatically inserts parentheses if != or == was typed after a&b, a|b or a^b where a and b are numbers.
   *
   * @return true if the '=' char was processed
   */
  public boolean handleEquality(Project project, Editor editor, PsiFile file, int offsetBefore) {
    return false;
  }

  /**
   * Automatically insert parentheses around the ?: when necessary.
   *
   * @return true if question mark was handled
   */
  public boolean handleQuestionMark(Project project, Editor editor, PsiFile file, int offsetBefore) {
    return false;
  }

  public boolean handleAnnotationParameter(Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return false;
  }

  public void autoPopupMemberLookup(@NotNull Project project, @NotNull Editor editor) {
    if (NewRdCompletionSupport.isFrontendRdCompletionOn(editor)) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor, new JavaTypedHandlerBase.FrontendAutoPopupMemberLookupCondition(editor));
    }
  }

  public void autoPopupJavadocLookup(@NotNull Project project, @NotNull Editor editor) {
  }

  public @NotNull Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (NewRdCompletionSupport.isFrontendRdCompletionOn(editor)) {
      return JavaTypedHandlerBase.doCheckAutoPopup(charTyped, project, editor, file);
    }
    return Result.CONTINUE;
  }
}
