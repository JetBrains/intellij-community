// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public class MissingMethodBodyFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_FIELD)) {
      // replace something like `void x` with `void x() {...}`
      // while it's ambiguous whether user wants a field or a method, declaring a field is easier (just append a semicolon),
      // so completing a method looks more useful
      if (BasicJavaAstTreeUtil.getInitializer(astNode) != null) return;
      ASTNode lastChild = astNode.getLastChildNode();
      PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(lastChild);
      if (!(psiElement instanceof PsiErrorElement)) return;
      if (!((PsiErrorElement)psiElement).getErrorDescription().equals(JavaPsiBundle.message("expected.semicolon"))) return;
      // Impossible modifiers for a method
      if (BasicJavaAstTreeUtil.hasModifierProperty(astNode, JavaTokenType.TRANSIENT_KEYWORD) ||
          BasicJavaAstTreeUtil.hasModifierProperty(astNode, JavaTokenType.VOLATILE_KEYWORD)) {
        return;
      }
      ASTNode typeElement = BasicJavaAstTreeUtil.getTypeElement(astNode);
      if (typeElement == null || !typeElement.getText().equals("void")) return;
      int endOffset = astNode.getTextRange().getEndOffset();
      editor.getDocument().insertString(endOffset, "()");
      editor.getDocument().insertString(endOffset + 2, "{}");
      editor.getCaretModel().moveToOffset(endOffset + 1);
      processor.registerUnresolvedError(endOffset + 1);
      processor.setSkipEnter(true);
      return;
    }
    if (!(BasicJavaAstTreeUtil.is(astNode, BASIC_METHOD))) return;
    if (!shouldMethodHaveBody(BasicJavaAstTreeUtil.toPsi(astNode))) return;

    final ASTNode body = BasicJavaAstTreeUtil.getCodeBlock(astNode);
    final Document doc = editor.getDocument();
    if (body != null) {
      // See IDEADEV-1093. This is quite hacky heuristic but it seem to be best we can do.
      String bodyText = body.getText();
      if (bodyText.startsWith("{")) {
        final ASTNode statement = BasicJavaAstTreeUtil.findChildByType(body, STATEMENT_SET);
        if (statement != null) {
          if (BasicJavaAstTreeUtil.is(statement, BASIC_DECLARATION_STATEMENT)) {
            PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(statement);
            if (psiElement != null && PsiTreeUtil.getDeepestLast(psiElement) instanceof PsiErrorElement) {
              ASTNode containingClass = BasicJavaAstTreeUtil.getParentOfType(astNode, CLASS_SET);
              if (containingClass != null && BasicJavaAstTreeUtil.getRBrace(containingClass) == null) {
                doc.insertString(body.getTextRange().getStartOffset() + 1, "\n}");
              }
            }
          }
        }
      }
      return;
    }
    ASTNode throwList = BasicJavaAstTreeUtil.findChildByType(astNode, BASIC_THROWS_LIST);
    if (throwList != null) {
      int endOffset = throwList.getTextRange().getEndOffset();
      if (endOffset < doc.getTextLength() && doc.getCharsSequence().charAt(endOffset) == ';') {
        doc.deleteString(endOffset, endOffset + 1);
      }
      processor.insertBracesWithNewLine(editor, endOffset);
    }
  }

  private static boolean shouldMethodHaveBody(@Nullable PsiElement method){
    return AfterSemicolonEnterProcessor.shouldHaveBody(
      BasicJavaAstTreeUtil.toNode(method));
  }
}
