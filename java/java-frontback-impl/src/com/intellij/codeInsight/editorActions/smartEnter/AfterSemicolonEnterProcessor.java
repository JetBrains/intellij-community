// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public class AfterSemicolonEnterProcessor implements ASTNodeEnterProcessor {

  @Override
  public boolean doEnter(@NotNull Editor editor, @NotNull ASTNode astNode, boolean isModified) {
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    if (psiElement == null) {
      return false;
    }
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_EXPRESSION_STATEMENT) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_DECLARATION_STATEMENT) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_DO_WHILE_STATEMENT) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_RETURN_STATEMENT) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_THROW_STATEMENT) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_BREAK_STATEMENT) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_CONTINUE_STATEMENT) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_YIELD_STATEMENT) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_ASSERT_STATEMENT) ||
        BasicJavaAstTreeUtil.is(astNode, Set.of(BASIC_FIELD, BASIC_ENUM_CONSTANT)) ||
        isImportStatementBase(psiElement) ||
        isMethodWithoutBody(psiElement)) {
      int errorOffset = getErrorElementOffset(psiElement);
      int elementEndOffset = astNode.getTextRange().getEndOffset();
      if (BasicJavaAstTreeUtil.is(astNode, BASIC_ENUM_CONSTANT)) {
        final CharSequence text = editor.getDocument().getCharsSequence();
        final int commaOffset = CharArrayUtil.shiftForwardUntil(text, elementEndOffset, ",");
        if (commaOffset < text.length()) {
          elementEndOffset = commaOffset + 1;
        }
      }

      if (errorOffset >= 0 && errorOffset < elementEndOffset) {
        final CharSequence text = editor.getDocument().getCharsSequence();
        if (text.charAt(errorOffset) == ' ' && text.charAt(errorOffset + 1) == ';') {
          errorOffset++;
        }
      }

      editor.getCaretModel().moveToOffset(errorOffset >= 0 ? errorOffset : elementEndOffset);
      return isModified;
    }
    return false;
  }

  static boolean shouldHaveBody(@Nullable ASTNode element) {
    if (element == null) {
      return false;
    }
    ASTNode containingClass = BasicJavaAstTreeUtil.getParentOfType(element, CLASS_SET);
    if (containingClass == null) return false;
    if (BasicJavaAstTreeUtil.hasModifierProperty(element, JavaTokenType.ABSTRACT_KEYWORD) ||
        BasicJavaAstTreeUtil.hasModifierProperty(element, JavaTokenType.NATIVE_KEYWORD)) {
      return false;
    }
    if (BasicJavaAstTreeUtil.hasModifierProperty(element, JavaTokenType.PRIVATE_KEYWORD)) return true;
    if (BasicJavaAstTreeUtil.isInterfaceEnumClassOrRecord(containingClass, JavaTokenType.INTERFACE_KEYWORD) &&
        !BasicJavaAstTreeUtil.hasModifierProperty(element, JavaTokenType.DEFAULT_KEYWORD) &&
        !BasicJavaAstTreeUtil.hasModifierProperty(element, JavaTokenType.STATIC_KEYWORD)) {
      return false;
    }
    return true;
  }

  private static boolean isMethodWithoutBody(@Nullable PsiElement psiElement){
    ASTNode node = BasicJavaAstTreeUtil.toNode(psiElement);
    return BasicJavaAstTreeUtil.is(node, BASIC_METHOD) &&
           !shouldHaveBody(node);
  }

  private static boolean isImportStatementBase(@Nullable PsiElement psiElement){
    ASTNode node = BasicJavaAstTreeUtil.toNode(psiElement);
    return
      BasicJavaAstTreeUtil.is(node, BASIC_IMPORT_STATEMENT) ||
      BasicJavaAstTreeUtil.is(node, BASIC_IMPORT_STATIC_STATEMENT);
  }

  private static int getErrorElementOffset(PsiElement elt) {
    final int[] offset = {-1};
    elt.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitErrorElement(@NotNull PsiErrorElement element) {
        if (offset[0] == -1) offset[0] = element.getTextRange().getStartOffset();
      }
    });
    return offset[0];
  }
}
