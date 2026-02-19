// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.intellij.psi.PsiModifier.ABSTRACT;
import static com.intellij.psi.PsiModifier.DEFAULT;
import static com.intellij.psi.PsiModifier.NATIVE;
import static com.intellij.psi.PsiModifier.PRIVATE;
import static com.intellij.psi.PsiModifier.STATIC;
import static com.intellij.psi.PsiModifier.TRANSIENT;
import static com.intellij.psi.PsiModifier.VOLATILE;

public class MissingMethodBodyFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, @NotNull PsiElement psiElement)
    throws IncorrectOperationException {
    if (psiElement instanceof PsiField field) {
      // replace something like `void x` with `void x() {...}`
      // while it's ambiguous whether user wants a field or a method, declaring a field is easier (just append a semicolon),
      // so completing a method looks more useful
      if (field.hasInitializer()) return;
      PsiElement lastChild = field.getLastChild();
      if (!(lastChild instanceof PsiErrorElement)) return;
      if (!((PsiErrorElement)lastChild).getErrorDescription().equals(JavaPsiBundle.message("expected.semicolon"))) return;
      PsiModifierList modifiers = field.getModifierList();
      if (modifiers == null) return;
      // Impossible modifiers for a method
      if (modifiers.hasExplicitModifier(TRANSIENT) || modifiers.hasExplicitModifier(VOLATILE)) return;
      if (!PsiTypes.voidType().equals(field.getType())) return;
      int endOffset = field.getTextRange().getEndOffset();
      editor.getDocument().insertString(endOffset, "(){}");
      editor.getCaretModel().moveToOffset(endOffset + 1);
      processor.registerUnresolvedError(endOffset + 1);
      processor.setSkipEnter(true);
      return;
    }
    if (!(psiElement instanceof PsiMethod method)) return;
    if (!shouldHaveBody(method)) return;

    final PsiCodeBlock body = method.getBody();
    final Document doc = editor.getDocument();
    if (body != null) {
      // See IDEADEV-1093. This is quite hacky heuristic but it seem to be best we can do.
      String bodyText = body.getText();
      if (bodyText.startsWith("{")) {
        final PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
          if (statements[0] instanceof PsiDeclarationStatement) {
            if (PsiTreeUtil.getDeepestLast(statements[0]) instanceof PsiErrorElement) {
              if (Objects.requireNonNull(method.getContainingClass()).getRBrace() == null) {
                doc.insertString(body.getTextRange().getStartOffset() + 1, "\n}");
              }
            }
          }
        }
      }
      return;
    }
    int endOffset = method.getThrowsList().getTextRange().getEndOffset();
    if (endOffset < doc.getTextLength() && doc.getCharsSequence().charAt(endOffset) == ';') {
      doc.deleteString(endOffset, endOffset + 1);
    }
    doc.insertString(endOffset, "{\n}");
  }

  static boolean shouldHaveBody(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    if (method.hasModifierProperty(ABSTRACT) || method.hasModifierProperty(NATIVE)) return false;
    if (method.hasModifierProperty(PRIVATE)) return true;
    if (containingClass.isInterface() && !method.hasModifierProperty(DEFAULT) && !method.hasModifierProperty(STATIC)) return false;
    return true;
  }
}
