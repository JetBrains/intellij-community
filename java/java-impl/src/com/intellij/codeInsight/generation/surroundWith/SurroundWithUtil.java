
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaJspElementType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public final class SurroundWithUtil {

  private static final Logger LOG = Logger.getInstance(SurroundWithUtil.class);

  private SurroundWithUtil() {
  }

  public static PsiElement[] moveDeclarationsOut(PsiElement block, PsiElement[] statements, boolean generateInitializers) {
    try{
      PsiManager psiManager = block.getManager();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiManager.getProject());
      ArrayList<PsiElement> array = new ArrayList<>();
      for (PsiElement statement : statements) {
        if (statement instanceof PsiDeclarationStatement) {
          PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
          if (needToDeclareOut(statements, declaration)) {
            PsiElement[] elements = declaration.getDeclaredElements();
            for (PsiElement element : elements) {
              PsiVariable var = (PsiVariable)element;
              PsiExpression initializer = var.getInitializer();
              if (initializer != null) {
                String name = var.getName();
                PsiExpressionStatement assignment = (PsiExpressionStatement)factory.createStatementFromText(name + "=x;", null);
                assignment = (PsiExpressionStatement)CodeStyleManager.getInstance(psiManager.getProject()).reformat(assignment);
                PsiAssignmentExpression expr = (PsiAssignmentExpression)assignment.getExpression();
                expr.getRExpression().replace(CommonJavaRefactoringUtil.convertInitializerToNormalExpression(initializer, var.getType()));
                assignment = (PsiExpressionStatement)block.addAfter(assignment, declaration);
                array.add(assignment);
              }
            }
            PsiDeclarationStatement newDeclaration;
            if (!array.isEmpty()) {
              PsiElement firstStatement = array.get(0);
              newDeclaration = (PsiDeclarationStatement)block.addBefore(declaration, firstStatement);
              declaration.delete();
            }
            else {
              newDeclaration = declaration;
            }
            elements = newDeclaration.getDeclaredElements();
            for (PsiElement element1 : elements) {
              PsiVariable var = (PsiVariable)element1;
              PsiExpression initializer = var.getInitializer();
              if (initializer != null) {
                PsiTypeElement typeElement = var.getTypeElement();
                if (typeElement != null &&
                    typeElement.isInferredType() &&
                    PsiTypesUtil.replaceWithExplicitType(typeElement) == null) {
                  continue;
                }
                if (!generateInitializers || var.hasModifierProperty(PsiModifier.FINAL)) {
                  initializer.delete();
                }
                else {
                  String defaultValue = PsiTypesUtil.getDefaultValueOfType(var.getType());
                  PsiExpression expr = factory.createExpressionFromText(defaultValue, null);
                  initializer.replace(expr);
                }
              }
            }
            continue;
          }
        }
        array.add(statement);
      }
      return PsiUtilCore.toPsiElementArray(array);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
      return statements;
    }
  }

  private static boolean needToDeclareOut(PsiElement[] statements, PsiDeclarationStatement statement) {
    PsiElement[] elements = statement.getDeclaredElements();
    PsiElement lastStatement = statements[statements.length - 1];
    int endOffset = lastStatement.getTextRange().getEndOffset();
    for (PsiElement element : elements) {
      if (element instanceof PsiVariable) {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
        PsiReference[] refs = ReferencesSearch.search(element, projectScope, false).toArray(PsiReference.EMPTY_ARRAY);
        if (refs.length > 0) {
          PsiReference lastRef = refs[refs.length - 1];
          if (lastRef.getElement().getTextOffset() > endOffset) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static TextRange getRangeToSelect (@NotNull PsiCodeBlock block) {
    PsiElement first = block.getFirstBodyElement();
    if (first instanceof PsiWhiteSpace) {
      first = first.getNextSibling();
    }
    if (first == null) {
      int offset = block.getTextRange().getStartOffset() + 1;
      return new TextRange(offset, offset);
    }
    PsiJavaToken rBrace = block.getRBrace();
    PsiElement last = rBrace.getPrevSibling();
    if (last instanceof PsiWhiteSpace) {
      last = last.getPrevSibling();
    }
    final int startOffset = first.getTextRange().getStartOffset();
    final int endOffset = last.getTextRange().getEndOffset();
    return startOffset <= endOffset ? new TextRange(startOffset, endOffset)
                                    : new TextRange(startOffset, startOffset);
  }

  /**
   * Performs indentation (if necessary) of the given code block that surrounds target statements.
   * <p/>
   * The main trick here is to handle situations like the one below:
   * <pre>
   *   void test() {
   *     // This is comment
   *         int i = 1;
   *   }
   * </pre>
   * The problem is that surround block doesn't contain any indent spaces, hence, the first statement is inserted to the
   * zero column. But we have a dedicated code style setting {@code 'keep comment at first column'}, i.e. the comment
   * will not be moved if that setting is checked.
   * <p/>
   * Current method handles that situation.
   *
   * @param container     code block that surrounds target statements
   * @param statements    target statements being surrounded
   */
  public static void indentCommentIfNecessary(@NotNull PsiCodeBlock container, PsiElement @Nullable [] statements) {
    if (statements == null || statements.length <= 0) {
      return;
    }

    PsiElement first = statements[0];
    ASTNode node = first.getNode();
    if (node == null || !ElementType.JAVA_COMMENT_BIT_SET.contains(node.getElementType())) {
      return;
    }

    ASTNode commentWsText = node.getTreePrev();
    if (commentWsText == null || !JavaJspElementType.WHITE_SPACE_BIT_SET.contains(commentWsText.getElementType())) {
      return;
    }

    int indent = 0;
    CharSequence text = commentWsText.getChars();
    for (int i = text.length() - 1; i >= 0; i--, indent++) {
      if (text.charAt(i) == '\n') {
        break;
      }
    }

    if (indent <= 0) {
      return;
    }

    PsiElement codeBlockWsElement = null;
    ASTNode codeBlockWsNode = null;
    boolean lbraceFound = false;
    final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(container.getProject());
    for (PsiElement codeBlockChild = container.getFirstChild(); codeBlockChild != null; codeBlockChild = codeBlockChild.getNextSibling()) {
      ASTNode childNode = codeBlockChild.getNode();
      if (childNode == null) {
        continue;
      }

      if (!lbraceFound) {
        if (JavaTokenType.LBRACE == childNode.getElementType()) {
          lbraceFound = true;
        }
        continue;
      }

      if (JavaJspElementType.WHITE_SPACE_BIT_SET.contains(childNode.getElementType())) {
        codeBlockWsElement = codeBlockChild;
        codeBlockWsNode = childNode;
        break;
      }
      else if (JavaTokenType.RBRACE == childNode.getElementType()) {
        break;
      }
    }

    if (codeBlockWsElement != null) {
      CharSequence existingWhiteSpaceText = codeBlockWsNode.getChars();
      int existingWhiteSpaceEndOffset = existingWhiteSpaceText.length();
      for (int i = existingWhiteSpaceEndOffset - 1; i >= 0; i--) {
        if (existingWhiteSpaceText.charAt(i) == '\n') {
          existingWhiteSpaceEndOffset = i;
          break;
        }
      }
      String newWsText = text.subSequence(text.length() - indent, text.length()).toString();

      // Add white spaces from all lines except the last one.
      if (existingWhiteSpaceEndOffset < existingWhiteSpaceText.length()) {
        newWsText = existingWhiteSpaceText.subSequence(0, existingWhiteSpaceEndOffset + 1).toString() + newWsText;
      }
      PsiElement indentElement = parserFacade.createWhiteSpaceFromText(newWsText);
      codeBlockWsElement.replace(indentElement);
    }
    else {
      PsiElement indentElement = parserFacade.createWhiteSpaceFromText(text.subSequence(text.length() - indent, text.length()).toString());
      container.add(indentElement);
    }
  }
}