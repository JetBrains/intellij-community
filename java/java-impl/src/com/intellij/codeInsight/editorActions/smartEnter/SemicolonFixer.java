// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SemicolonFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (fixReturn(editor, psiElement)) return;
    if (fixForUpdate(editor, psiElement)) return;
    fixAfterLastValidElement(editor, psiElement);
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

  private static boolean fixForUpdate(@NotNull Editor editor, @Nullable PsiElement psiElement) {
    if (!(psiElement instanceof PsiForStatement)) {
      return false;
    }

    PsiForStatement forStatement = (PsiForStatement)psiElement;
    PsiExpression condition = forStatement.getCondition();
    if (forStatement.getUpdate() != null || condition == null) {
      return false;
    }

    TextRange range = condition.getTextRange();
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    for (int i = range.getEndOffset() - 1, max = forStatement.getTextRange().getEndOffset(); i < max; i++) {
      if (text.charAt(i) == ';') {
        return false;
      }
    }

    String toInsert = ";";
    if (CodeStyle.getSettings(psiElement.getContainingFile()).getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_SEMICOLON) {
      toInsert += " ";
    }
    document.insertString(range.getEndOffset(), toInsert);
    return true;
  }

  private static boolean fixAfterLastValidElement(@NotNull Editor editor, @Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiExpressionStatement ||
        psiElement instanceof PsiDeclarationStatement ||
        psiElement instanceof PsiImportStatementBase ||
        psiElement instanceof PsiDoWhileStatement ||
        psiElement instanceof PsiReturnStatement ||
        psiElement instanceof PsiThrowStatement ||
        psiElement instanceof PsiBreakStatement ||
        psiElement instanceof PsiContinueStatement ||
        psiElement instanceof PsiAssertStatement ||
        psiElement instanceof PsiPackageStatement ||
        isStandaloneField(psiElement) ||
        psiElement instanceof PsiMethod && ((PsiMethod)psiElement).getBody() == null && !MissingMethodBodyFixer.shouldHaveBody((PsiMethod)psiElement) ||
        psiElement instanceof PsiRequiresStatement ||
        psiElement instanceof PsiPackageAccessibilityStatement ||
        psiElement instanceof PsiUsesStatement ||
        psiElement instanceof PsiProvidesStatement)
    {
      String text = psiElement.getText();

      int tailLength = 0;
      ASTNode leaf = TreeUtil.findLastLeaf(psiElement.getNode());
      while (leaf != null && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(leaf.getElementType())) {
        tailLength += leaf.getTextLength();
        leaf = TreeUtil.prevLeaf(leaf);
      }
      if (leaf == null) {
        return false;
      }

      if (tailLength > 0) {
        text = text.substring(0, text.length() - tailLength);
      }

      int insertionOffset = leaf.getTextRange().getEndOffset();
      Document doc = editor.getDocument();
      if (psiElement instanceof PsiField && ((PsiField)psiElement).hasModifierProperty(PsiModifier.ABSTRACT)) {
        // abstract rarely seem to be field. It is rather incomplete method.
        doc.insertString(insertionOffset, "()");
        insertionOffset += "()".length();
      }

      // Like:
      // assert x instanceof Type
      // String s = "hello";
      // Here, String is parsed as name of the pattern variable, and we have an assignment, instead of declaration 
      if (psiElement.getLastChild() instanceof PsiErrorElement error &&
          error.getPrevSibling() instanceof PsiInstanceOfExpression instanceOf &&
          instanceOf.getLastChild() instanceof PsiTypeTestPattern typePattern &&
          typePattern.getPatternVariable() != null &&
          PsiTreeUtil.skipWhitespacesForward(psiElement) instanceof PsiExpressionStatement exprStmt &&
          exprStmt.getExpression() instanceof PsiAssignmentExpression assignment &&
          assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        PsiPatternVariable variable = typePattern.getPatternVariable();
        PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier.getPrevSibling() instanceof PsiWhiteSpace ws && ws.getText().contains("\n") &&
            editor.getCaretModel().getOffset() < identifier.getTextRange().getStartOffset()) {
          insertionOffset = ws.getTextRange().getStartOffset();
        }
      }

      if (!StringUtil.endsWithChar(text, ';')) {
        PsiElement parent = psiElement.getParent();
        String toInsert = ";";
        if (parent instanceof PsiForStatement) {
          if (((PsiForStatement)parent).getUpdate() == psiElement) {
            return false;
          }
          if (CodeStyle.getSettings(psiElement.getContainingFile()).getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_SEMICOLON) {
            toInsert += " ";
          }
        }

        doc.insertString(insertionOffset, toInsert);
        return true;
      }
    }

    return false;
  }

  private static boolean isStandaloneField(@Nullable PsiElement psiElement) {
    return psiElement instanceof PsiField &&
           !(psiElement instanceof PsiEnumConstant) &&
           !PsiJavaPatterns.psiElement().beforeLeaf(PsiJavaPatterns.psiElement().withText(",")).accepts(psiElement);
  }
}