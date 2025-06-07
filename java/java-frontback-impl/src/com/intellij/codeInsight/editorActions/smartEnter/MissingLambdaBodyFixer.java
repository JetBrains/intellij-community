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
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public class MissingLambdaBodyFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    ASTNode body = null;
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_LAMBDA_EXPRESSION)) {
      final ASTNode lastChild = astNode.getLastChildNode();
      if (BasicJavaAstTreeUtil.is(lastChild, EXPRESSION_SET) ||
          BasicJavaAstTreeUtil.is(lastChild, BASIC_CODE_BLOCK)) {
        body = lastChild;
      }
    }
    else if (BasicJavaAstTreeUtil.is(astNode, BASIC_SWITCH_LABELED_RULE)) {
      body = BasicJavaAstTreeUtil.getRuleBody(astNode);
    }
    else {
      return;
    }
    if (body != null) return;
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    if (psiElement == null) {
      return;
    }
    PsiElement arrow = PsiTreeUtil.getDeepestVisibleLast(psiElement);
    if (arrow == null || !arrow.getNode().getElementType().equals(JavaTokenType.ARROW)) return;
    int offset = arrow.getTextRange().getEndOffset();
    processor.insertBracesWithNewLine(editor, offset);
    editor.getCaretModel().moveToOffset(offset + 1);
    processor.commit(editor);
    processor.reformat(psiElement);
    processor.setSkipEnter(BasicJavaAstTreeUtil.is(astNode, BASIC_LAMBDA_EXPRESSION));
  }
}