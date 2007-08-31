
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

public class SurroundWithUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil");
  static PsiElement[] moveDeclarationsOut(PsiElement block, PsiElement[] statements, boolean generateInitializers) {
    try{
      PsiManager psiManager = block.getManager();
      PsiElementFactory factory = psiManager.getElementFactory();
      ArrayList<PsiElement> array = new ArrayList<PsiElement>();
      for (PsiElement statement : statements) {
        if (statement instanceof PsiDeclarationStatement) {
          PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
          if (needToDeclareOut(block, statements, declaration)) {
            PsiElement[] elements = declaration.getDeclaredElements();
            for (PsiElement element : elements) {
              PsiVariable var = (PsiVariable)element;
              PsiExpression initializer = var.getInitializer();
              if (initializer != null) {
                String name = var.getName();
                PsiExpressionStatement assignment = (PsiExpressionStatement)factory.createStatementFromText(name + "=x;", null);
                assignment = (PsiExpressionStatement)CodeStyleManager.getInstance(psiManager.getProject()).reformat(assignment);
                PsiAssignmentExpression expr = (PsiAssignmentExpression)assignment.getExpression();
                expr.getRExpression().replace(initializer);
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
      return array.toArray(new PsiElement[array.size()]);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
      return statements;
    }
  }

  private static boolean needToDeclareOut(PsiElement block, PsiElement[] statements, PsiDeclarationStatement statement) {
    PsiSearchHelper helper = block.getManager().getSearchHelper();
    PsiElement[] elements = statement.getDeclaredElements();
    PsiElement lastStatement = statements[statements.length - 1];
    int endOffset = lastStatement.getTextRange().getEndOffset();
    for (PsiElement element : elements) {
      if (element instanceof PsiVariable) {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
        PsiReference[] refs = helper.findReferences(element, projectScope, false);
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

  public static TextRange getRangeToSelect (PsiCodeBlock block) {
    PsiElement first = block.getFirstBodyElement();
    if (first instanceof PsiWhiteSpace) {
      first = first.getNextSibling();
    }
    if (first == null) {
      int offset = block.getTextRange().getStartOffset() + 1;
      return new TextRange(offset, offset);
    }
    PsiElement last = block.getRBrace().getPrevSibling();
    if (last instanceof PsiWhiteSpace) {
      last = last.getPrevSibling();
    }
    return new TextRange(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
  }
}