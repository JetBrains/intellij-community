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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class DeclarationJoinLinesHandler implements JoinLinesHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler");

  @Override
  public int tryJoinLines(@NotNull final Document document, @NotNull final PsiFile file, final int start, final int end) {
    PsiElement elementAtStartLineEnd = file.findElementAt(start);
    PsiElement elementAtNextLineStart = file.findElementAt(end);
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return -1;

    // first line.
    if (!(elementAtStartLineEnd instanceof PsiJavaToken)) return -1;
    PsiJavaToken lastFirstLineToken = (PsiJavaToken)elementAtStartLineEnd;
    if (lastFirstLineToken.getTokenType() != JavaTokenType.SEMICOLON) return -1;
    if (!(lastFirstLineToken.getParent() instanceof PsiLocalVariable)) return -1;
    PsiLocalVariable var = (PsiLocalVariable)lastFirstLineToken.getParent();

    if (!(var.getParent() instanceof PsiDeclarationStatement)) return -1;
    PsiDeclarationStatement decl = (PsiDeclarationStatement)var.getParent();
    if (decl.getDeclaredElements().length > 1) return -1;

    //second line.
    if (!(elementAtNextLineStart instanceof PsiJavaToken)) return -1;
    PsiJavaToken firstNextLineToken = (PsiJavaToken)elementAtNextLineStart;
    if (firstNextLineToken.getTokenType() != JavaTokenType.IDENTIFIER) return -1;
    if (!(firstNextLineToken.getParent() instanceof PsiReferenceExpression)) return -1;
    PsiReferenceExpression ref = (PsiReferenceExpression)firstNextLineToken.getParent();
    PsiElement refResolved = ref.resolve();

    PsiManager psiManager = ref.getManager();
    if (!psiManager.areElementsEquivalent(refResolved, var)) return -1;
    if (!(ref.getParent() instanceof PsiAssignmentExpression)) return -1;
    PsiAssignmentExpression assignment = (PsiAssignmentExpression)ref.getParent();
    if (!(assignment.getParent() instanceof PsiExpressionStatement)) return -1;

    if (ReferencesSearch.search(var, new LocalSearchScope(assignment.getRExpression()), false).findFirst() != null) {
      return -1;
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    final PsiExpression initializerExpression = getInitializerExpression(var, assignment);
    if (initializerExpression == null) return -1;

    PsiExpressionStatement statement = (PsiExpressionStatement)assignment.getParent();

    int startOffset = decl.getTextRange().getStartOffset();
    try {
      PsiDeclarationStatement newDecl = factory.createVariableDeclarationStatement(var.getName(), var.getType(), initializerExpression);
      PsiVariable newVar = (PsiVariable)newDecl.getDeclaredElements()[0];
      if (var.getModifierList().getText().length() > 0) {
        PsiUtil.setModifierProperty(newVar, PsiModifier.FINAL, true);
      }
      newVar.getModifierList().replace(var.getModifierList());
      PsiVariable variable = (PsiVariable)newDecl.getDeclaredElements()[0];
      final int offsetBeforeEQ = variable.getNameIdentifier().getTextRange().getEndOffset();
      final int offsetAfterEQ = variable.getInitializer().getTextRange().getStartOffset() + 1;
      newDecl = (PsiDeclarationStatement)CodeStyleManager.getInstance(psiManager).reformatRange(newDecl, offsetBeforeEQ, offsetAfterEQ);

      PsiElement child = statement.getLastChild();
      while (child instanceof PsiComment || child instanceof PsiWhiteSpace) {
        child = child.getPrevSibling();
      }
      if (child != null && child.getNextSibling() != null) {
        newDecl.addRangeBefore(child.getNextSibling(), statement.getLastChild(), null);
      }

      decl.replace(newDecl);
      statement.delete();
      return startOffset + newDecl.getTextRange().getEndOffset() - newDecl.getTextRange().getStartOffset();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return -1;
    }
  }

  public static PsiExpression getInitializerExpression(PsiLocalVariable var,
                                                       PsiAssignmentExpression assignment) {
    return getInitializerExpression(var.getInitializer(), 
                                    assignment);
  }

  public static PsiExpression getInitializerExpression(PsiExpression initializer,
                                                       PsiAssignmentExpression assignment) {
    PsiExpression initializerExpression;
    final IElementType originalOpSign = assignment.getOperationTokenType();
    final PsiExpression rExpression = assignment.getRExpression();
    if (originalOpSign == JavaTokenType.EQ) {
      initializerExpression = rExpression;
    }
    else {
      if (initializer == null) return null;
      String opSign = null;
      if (originalOpSign == JavaTokenType.ANDEQ) {
        opSign = "&";
      }
      else if (originalOpSign == JavaTokenType.ASTERISKEQ) {
        opSign = "*";
      }
      else if (originalOpSign == JavaTokenType.DIVEQ) {
        opSign = "/";
      }
      else if (originalOpSign == JavaTokenType.GTGTEQ) {
        opSign = ">>";
      }
      else if (originalOpSign == JavaTokenType.GTGTGTEQ) {
        opSign = ">>>";
      }
      else if (originalOpSign == JavaTokenType.LTLTEQ) {
        opSign = "<<";
      }
      else if (originalOpSign == JavaTokenType.MINUSEQ) {
        opSign = "-";
      }
      else if (originalOpSign == JavaTokenType.OREQ) {
        opSign = "|";
      }
      else if (originalOpSign == JavaTokenType.PERCEQ) {
        opSign = "%";
      }
      else if (originalOpSign == JavaTokenType.PLUSEQ) {
        opSign = "+";
      }
      else if (originalOpSign == JavaTokenType.XOREQ) {
        opSign = "^";
      }

      try {
        final Project project = assignment.getProject();
        String initializerText = initializer.getText() + opSign;
        final String rightText = rExpression.getText();
        if (ParenthesesUtils.areParenthesesNeeded(assignment.getOperationSign(), rExpression)) {
          initializerText += "(" + rightText + ")";
        }
        else {
          initializerText += rightText;
        }
        initializerExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(initializerText, assignment);
        initializerExpression = (PsiExpression)CodeStyleManager.getInstance(project).reformat(initializerExpression);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }
    return initializerExpression;
  }
}
