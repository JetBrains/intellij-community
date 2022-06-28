/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.Objects;

import static com.intellij.psi.PsiModifier.*;

public class MissingMethodBodyFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiField) {
      // replace something like `void x` with `void x() {...}`
      // while it's ambiguous whether user wants a field or a method, declaring a field is easier (just append a semicolon),
      // so completing a method looks more useful
      PsiField field = (PsiField)psiElement;
      if (field.hasInitializer()) return;
      PsiElement lastChild = field.getLastChild();
      if (!(lastChild instanceof PsiErrorElement)) return;
      if (!((PsiErrorElement)lastChild).getErrorDescription().equals(JavaErrorBundle.message("expected.semicolon"))) return;
      PsiModifierList modifiers = field.getModifierList();
      if (modifiers == null) return;
      // Impossible modifiers for a method
      if (modifiers.hasExplicitModifier(TRANSIENT) || modifiers.hasExplicitModifier(VOLATILE)) return;
      if (!PsiType.VOID.equals(field.getType())) return;
      int endOffset = field.getTextRange().getEndOffset();
      editor.getDocument().insertString(endOffset, "(){}");
      editor.getCaretModel().moveToOffset(endOffset + 1);
      processor.registerUnresolvedError(endOffset + 1);
      processor.setSkipEnter(true);
      return;
    }
    if (!(psiElement instanceof PsiMethod)) return;
    PsiMethod method = (PsiMethod) psiElement;
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
