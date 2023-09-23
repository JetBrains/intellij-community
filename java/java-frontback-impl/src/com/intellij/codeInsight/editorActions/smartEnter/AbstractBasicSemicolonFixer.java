// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET;
import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public abstract class AbstractBasicSemicolonFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    if (fixReturn(editor, psiElement)) return;
    if (fixForUpdate(editor, astNode)) return;
    fixAfterLastValidElement(editor, astNode);
  }

  protected abstract boolean fixReturn(@NotNull Editor editor, @Nullable PsiElement astNode);

  protected abstract boolean getSpaceAfterSemicolon(@NotNull PsiElement psiElement);

  private boolean fixForUpdate(@NotNull Editor editor, @Nullable ASTNode astNode) {
    if (!(BasicJavaAstTreeUtil.is(astNode, BASIC_FOR_STATEMENT))) {
      return false;
    }

    ASTNode condition = BasicJavaAstTreeUtil.getForCondition(astNode);
    if (BasicJavaAstTreeUtil.getForUpdate(astNode) != null || condition == null) {
      return false;
    }

    TextRange range = condition.getTextRange();
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    for (int i = range.getEndOffset() - 1, max = astNode.getTextRange().getEndOffset(); i < max; i++) {
      if (text.charAt(i) == ';') {
        return false;
      }
    }

    String toInsert = ";";
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    if (psiElement != null && getSpaceAfterSemicolon(psiElement)) {
      toInsert += " ";
    }
    document.insertString(range.getEndOffset(), toInsert);
    return true;
  }


  private void fixAfterLastValidElement(@NotNull Editor editor, @Nullable ASTNode astNode) {
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    if (astNode == null || psiElement == null) {
      return;
    }
    if (
      BasicJavaAstTreeUtil.is(astNode, BASIC_EXPRESSION_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_DECLARATION_STATEMENT) ||
      isImportStatementBase(psiElement) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_DO_WHILE_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_RETURN_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_THROW_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_BREAK_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_CONTINUE_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_YIELD_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_ASSERT_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_PACKAGE_STATEMENT) ||
      isStandaloneField(psiElement) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_METHOD) &&
      BasicJavaAstTreeUtil.getCodeBlock(astNode) == null &&
      !isMethodShouldHaveBody(psiElement) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_REQUIRES_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_OPENS_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_EXPORTS_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_USES_STATEMENT) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_PROVIDES_STATEMENT)) {
      String text = astNode.getText();

      int tailLength = 0;
      ASTNode leaf = TreeUtil.findLastLeaf(astNode);
      while (leaf != null && BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(leaf.getElementType())) {
        tailLength += leaf.getTextLength();
        leaf = TreeUtil.prevLeaf(leaf);
      }
      if (leaf == null) {
        return;
      }

      if (tailLength > 0) {
        text = text.substring(0, text.length() - tailLength);
      }

      int insertionOffset = leaf.getTextRange().getEndOffset();
      Document doc = editor.getDocument();
      if (BasicJavaAstTreeUtil.is(astNode, BASIC_FIELD) &&
          (BasicJavaAstTreeUtil.hasModifierProperty(astNode, JavaTokenType.ABSTRACT_KEYWORD))) {
        // abstract rarely seem to be field. It is rather incomplete method.
        doc.insertString(insertionOffset, "()");
        insertionOffset += "()".length();
      }

      // Like:
      // assert x instanceof Type
      // String s = "hello";
      // Here, String is parsed as name of the pattern variable, and we have an assignment, instead of declaration
      ASTNode error = astNode.getLastChildNode();
      if (BasicJavaAstTreeUtil.is(error, TokenType.ERROR_ELEMENT) &&
          BasicJavaAstTreeUtil.is(error.getTreePrev(), BASIC_INSTANCE_OF_EXPRESSION) &&
          BasicJavaAstTreeUtil.is(error.getTreePrev().getLastChildNode(), BASIC_TYPE_TEST_PATTERN)) {
        ASTNode variable = BasicJavaAstTreeUtil.getPatternVariable(error.getTreePrev().getLastChildNode());
        PsiElement skipWhitespacesForward = PsiTreeUtil.skipWhitespacesForward(psiElement);
        ASTNode assignmentExpr = BasicJavaAstTreeUtil.getExpression(BasicJavaAstTreeUtil.toNode(skipWhitespacesForward));
        if (variable != null &&
            BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(skipWhitespacesForward), BASIC_EXPRESSION_STATEMENT) &&
            BasicJavaAstTreeUtil.is(assignmentExpr, BASIC_ASSIGNMENT_EXPRESSION) &&
            JavaTokenType.EQ.equals(BasicJavaAstTreeUtil.getAssignmentOperationTokenType(assignmentExpr))) {
          ASTNode identifier = BasicJavaAstTreeUtil.getNameIdentifier(variable);
          if (identifier != null &&
              BasicJavaAstTreeUtil.toPsi(identifier.getTreePrev()) instanceof PsiWhiteSpace ws &&
              ws.getText().contains("\n") &&
              editor.getCaretModel().getOffset() < identifier.getTextRange().getStartOffset()) {
            insertionOffset = ws.getTextRange().getStartOffset();
          }
        }
      }

      if (!StringUtil.endsWithChar(text, ';')) {
        ASTNode parent = astNode.getTreeParent();
        String toInsert = ";";
        if (BasicJavaAstTreeUtil.is(parent, BASIC_FOR_STATEMENT)) {
          if (BasicJavaAstTreeUtil.getForUpdate(parent) == astNode) {
            return;
          }
          if (getSpaceAfterSemicolon(psiElement)) {
            toInsert += " ";
          }
        }

        doc.insertString(insertionOffset, toInsert);
      }
    }
  }

  private static boolean isMethodShouldHaveBody(@Nullable PsiElement psiElement){
    return AfterSemicolonEnterProcessor.shouldHaveBody(BasicJavaAstTreeUtil.toNode(psiElement));
  }

  protected abstract boolean isImportStatementBase(@Nullable PsiElement psiElement);

  private static boolean isStandaloneField(@Nullable PsiElement psiElement) {
    if (psiElement == null || !BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(psiElement), BASIC_FIELD)) return false;
    PsiElement node = PsiTreeUtil.nextLeaf(psiElement, true);
    if (node == null) {
      return false;
    }
    return !",".equals(node.getText());
  }
}