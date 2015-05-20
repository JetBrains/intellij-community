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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 3:35:49 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class SemicolonFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    boolean fixed = fixReturn(editor, psiElement) || fixForUpdate(editor, psiElement, processor)
                    || fixAfterLastValidElement(editor, psiElement);
  }

  private static boolean fixReturn(@NotNull Editor editor, @Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiReturnStatement) {
      PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class, true, PsiLambdaExpression.class);
      if (method != null && PsiType.VOID.equals(method.getReturnType())) {
        PsiReturnStatement stmt = (PsiReturnStatement)psiElement;
        if (stmt.getReturnValue() != null) {
          Document doc = editor.getDocument();
          doc.insertString(stmt.getTextRange().getStartOffset() + "return".length(), ";");
          return true;
        }
      }
    }
    return false;
  }

  private static boolean fixForUpdate(@NotNull Editor editor, @Nullable PsiElement psiElement, @NotNull JavaSmartEnterProcessor processor) {
    if (!(psiElement instanceof PsiForStatement)) {
      return false;
    }
    
    PsiForStatement forStatement = (PsiForStatement)psiElement;
    final PsiExpression condition = forStatement.getCondition();
    if (forStatement.getUpdate() != null || condition == null) {
      return false;
    }

    final TextRange range = condition.getTextRange();
    final Document document = editor.getDocument();
    final CharSequence text = document.getCharsSequence();
    for (int i = range.getEndOffset() - 1, max = forStatement.getTextRange().getEndOffset(); i < max; i++) {
      if (text.charAt(i) == ';') {
        return false;
      }
    }

    String toInsert = ";";
    final Project project = editor.getProject();
    if (project != null && CodeStyleSettingsManager.getSettings(project).SPACE_AFTER_SEMICOLON) {
      toInsert += " ";
    }
    document.insertString(range.getEndOffset(), toInsert);
    return true;
  }
  
  private static boolean fixAfterLastValidElement(@NotNull Editor editor, @Nullable PsiElement psiElement) {
    if (psiElement == null ||
        !(psiElement instanceof PsiExpressionStatement) &&
        !(psiElement instanceof PsiDeclarationStatement) &&
        !(psiElement instanceof PsiImportStatementBase) &&
        !(psiElement instanceof PsiDoWhileStatement) &&
        !(psiElement instanceof PsiReturnStatement) &&
        !(psiElement instanceof PsiThrowStatement) &&
        !(psiElement instanceof PsiBreakStatement) &&
        !(psiElement instanceof PsiContinueStatement) &&
        !(psiElement instanceof PsiAssertStatement) &&
        !(psiElement instanceof PsiPackageStatement) &&
        (!(psiElement instanceof PsiField) || psiElement instanceof PsiEnumConstant) &&
        (!(psiElement instanceof PsiMethod) || ((PsiMethod)psiElement).getBody() != null || MissingMethodBodyFixer.shouldHaveBody((PsiMethod)psiElement))) {
      return false;
    }
    String text = psiElement.getText();

    int tailLength = 0;
    ASTNode leaf = TreeUtil.findLastLeaf(psiElement.getNode());
    while (leaf != null && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(leaf.getElementType())) {
      tailLength += leaf.getTextLength();
      leaf = TreeUtil.prevLeaf(leaf);
    }

    if (tailLength > 0) {
      text = text.substring(0, text.length() - tailLength);
    }

    if (leaf == null) {
      return false;
    }
    int insertionOffset = leaf.getTextRange().getEndOffset();
    Document doc = editor.getDocument();
    if (psiElement instanceof PsiField && ((PsiField)psiElement).hasModifierProperty(PsiModifier.ABSTRACT)) {
      // abstract rarely seem to be field. It is rather incomplete method.
      doc.insertString(insertionOffset, "()");
      insertionOffset += "()".length();
    }

    if (!StringUtil.endsWithChar(text, ';')) {
      final PsiElement parent = psiElement.getParent();
      String toInsert = ";";
      if (parent instanceof PsiForStatement) {
        if (((PsiForStatement)parent).getUpdate() == psiElement) {
          return false;
        }
        else {
          final Project project = editor.getProject();
          if (project != null && CodeStyleSettingsManager.getSettings(project).SPACE_AFTER_SEMICOLON) {
            toInsert += " ";
          }
        }
      }

      doc.insertString(insertionOffset, toInsert);
      return true;
    }
    return false;
  }
}
