// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class JavaDocMarkdownEnterHandler extends EnterHandlerDelegateAdapter {
  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    if (!(file instanceof PsiJavaFile) || !file.isValid()) return Result.Continue;

    PsiElement caretElement = file.findElementAt(caretOffset.get());
    if (caretElement == null) return Result.Continue;

    PsiElement currentElement = caretElement.getPrevSibling();
    // EOL whitespace is not useful, we only need the tokens behind it
    if (currentElement instanceof PsiWhiteSpace) {
      currentElement = currentElement.getPrevSibling();
    }

    if (!shouldInsertLeadingTokens(currentElement)) {
      return Result.Continue;
    }
    Document document = editor.getDocument();

    document.insertString(caretOffset.get(), "///");
    caretAdvance.set(3);

    return Result.DefaultForceIndent;
  }

  /**
   * Verifies whether we should automatically add the leading slashes
   *
   * @param element a doc element found at the caret offset
   * @return If the javadoc is tied to a method/a class it should return true otherwise false
   */
  private static boolean shouldInsertLeadingTokens(PsiElement element) {
    PsiDocComment docComment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class, false, PsiMember.class);
    if (docComment == null || !docComment.isMarkdownComment()) return false;

    return !JavaDocUtil.isDanglingDocComment(docComment, true);
  }
}
