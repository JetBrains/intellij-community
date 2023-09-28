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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public class MissingLoopBodyFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    ASTNode loopStatement = getLoopParent(astNode);
    if (loopStatement == null) return;

    final Document doc = editor.getDocument();
    ASTNode body;
    if (BasicJavaAstTreeUtil.is(loopStatement, BASIC_FOR_STATEMENT)) {
      body = BasicJavaAstTreeUtil.getForBody(loopStatement);
    }
    else if (BasicJavaAstTreeUtil.is(loopStatement, BASIC_FOREACH_STATEMENT)) {
      body = BasicJavaAstTreeUtil.getForeachBody(loopStatement);
    }
    else if (BasicJavaAstTreeUtil.is(loopStatement, BASIC_WHILE_STATEMENT)) {
      body = BasicJavaAstTreeUtil.getWhileBody(loopStatement);
    }
    else {
      return;
    }

    if (BasicJavaAstTreeUtil.is(body, BASIC_BLOCK_STATEMENT)) return;
    if (body != null && startLine(doc, body) == startLine(doc, loopStatement)) return;

    fixLoopBody(editor, processor, loopStatement, doc, body);
  }

  private static ASTNode getLoopParent(@NotNull ASTNode element) {
    ASTNode statement = BasicJavaAstTreeUtil.getParentOfType(element, ParentAwareTokenSet.create(BASIC_FOREACH_STATEMENT,
                                                                                                 BASIC_FOR_STATEMENT,
                                                                                                 BASIC_WHILE_STATEMENT));
    if (statement == null) return null;
    if (BasicJavaAstTreeUtil.is(statement, BASIC_FOREACH_STATEMENT)) {
      return isForEachApplicable(statement, element) ? statement : null;
    }
    if (BasicJavaAstTreeUtil.is(statement, BASIC_FOR_STATEMENT)) {
      return isForApplicable(statement, element) ? statement : null;
    }
    if (BasicJavaAstTreeUtil.is(statement, BASIC_WHILE_STATEMENT)) {
      return statement;
    }
    return null;
  }

  private static boolean isForApplicable(ASTNode statement, ASTNode astNode) {
    ASTNode init = BasicJavaAstTreeUtil.getForInitialization(statement);
    ASTNode update = BasicJavaAstTreeUtil.getForUpdate(statement);
    ASTNode check = BasicJavaAstTreeUtil.getForCondition(statement);

    return isValidChild(init, astNode) || isValidChild(update, astNode) || isValidChild(check, astNode);
  }

  private static boolean isValidChild(ASTNode ancestorNode, ASTNode node) {
    PsiElement ancestor = BasicJavaAstTreeUtil.toPsi(ancestorNode);
    PsiElement element = BasicJavaAstTreeUtil.toPsi(node);
    if (ancestor != null && element != null) {
      if (PsiTreeUtil.isAncestor(ancestor, element, false)) {
        if (PsiTreeUtil.hasErrorElements(ancestor)) return false;
        return true;
      }
    }

    return false;
  }

  private static boolean isForEachApplicable(ASTNode statement, ASTNode astNode) {
    ASTNode iterated = BasicJavaAstTreeUtil.getForEachIteratedValue(statement);
    ASTNode parameter = BasicJavaAstTreeUtil.getForEachIterationParameter(statement);
    PsiElement iteratedElement = BasicJavaAstTreeUtil.toPsi(iterated);
    PsiElement parameterElement = BasicJavaAstTreeUtil.toPsi(parameter);
    PsiElement element = BasicJavaAstTreeUtil.toPsi(astNode);
    return element != null &&
           (PsiTreeUtil.isAncestor(iteratedElement, element, false) ||
            PsiTreeUtil.isAncestor(parameterElement, element, false));
  }

  private static int startLine(Document doc, ASTNode astNode) {
    return doc.getLineNumber(astNode.getTextRange().getStartOffset());
  }

  private static void fixLoopBody(@NotNull Editor editor,
                                  @NotNull AbstractBasicJavaSmartEnterProcessor processor,
                                  @NotNull ASTNode loop,
                                  @NotNull Document doc,
                                  @Nullable ASTNode body) {
    ASTNode eltToInsertAfter = BasicJavaAstTreeUtil.getRParenth(loop);
    PsiElement loopElement = BasicJavaAstTreeUtil.toPsi(loop);
    if (body != null && eltToInsertAfter != null) {
      PsiElement bodyElement = BasicJavaAstTreeUtil.toPsi(body);
      if (loopElement != null && bodyElement != null && bodyIsIndented(loopElement, bodyElement)) {
        int endOffset = body.getTextRange().getEndOffset();
        doc.insertString(endOffset, "\n");
        processor.insertCloseBrace(editor, endOffset + 1);
        int offset = eltToInsertAfter.getTextRange().getEndOffset();
        doc.insertString(offset, "{");
        editor.getCaretModel().moveToOffset(endOffset + "{".length());
        processor.setSkipEnter(true);
        processor.reformat(loopElement);
        return;
      }
    }
    boolean needToClose = false;
    if (eltToInsertAfter == null) {
      eltToInsertAfter = loop;
      needToClose = true;
    }
    int offset = eltToInsertAfter.getTextRange().getEndOffset();
    if (needToClose) {
      if (BasicJavaAstTreeUtil.getLParenth(loop) == null) {
        doc.insertString(offset, "()");
        offset += 2;
      } else {
        doc.insertString(offset, ")");
        offset++;
      }
    }
    processor.insertBraces(editor, offset);
    editor.getCaretModel().moveToOffset(offset);
  }

  private static boolean bodyIsIndented(@NotNull PsiElement loop, @NotNull PsiElement body) {
    PsiWhiteSpace beforeBody = ObjectUtils.tryCast(body.getPrevSibling(), PsiWhiteSpace.class);
    if (beforeBody == null) return false;
    PsiWhiteSpace beforeLoop = ObjectUtils.tryCast(loop.getPrevSibling(), PsiWhiteSpace.class);
    if (beforeLoop == null) return false;
    String beforeBodyText = beforeBody.getText();
    String beforeLoopText = beforeLoop.getText();
    int beforeBodyLineBreak = beforeBodyText.lastIndexOf('\n');
    if (beforeBodyLineBreak == -1) return false;
    int beforeLoopLineBreak = beforeLoopText.lastIndexOf('\n');
    if (beforeLoopLineBreak == -1) return false;
    return beforeBodyText.length() - beforeBodyLineBreak > beforeLoopText.length() - beforeLoopLineBreak;
  }
}
