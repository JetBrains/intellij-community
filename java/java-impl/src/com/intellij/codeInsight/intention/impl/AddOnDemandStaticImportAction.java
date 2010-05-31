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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.highlighting.*;
import com.intellij.codeInsight.intention.*;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import gnu.trove.*;
import org.jetbrains.annotations.*;

/**
 * @author ven
 */
public class AddOnDemandStaticImportAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction");

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.on.demand.static.import.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return false;
    if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
    if (refExpr.getParent() instanceof PsiReferenceExpression &&
        isParameterizedReference((PsiReferenceExpression)refExpr.getParent())) return false;

    PsiElement resolved = refExpr.resolve();
    if (!(resolved instanceof PsiClass)) {
      return false;
    }
    PsiClass psiClass = (PsiClass)resolved;
    PsiFile file = refExpr.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return false;
    PsiImportList importList = ((PsiJavaFile)file).getImportList();
    if (importList == null) return false;
    for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
      PsiClass staticResolve = statement.resolveTargetClass();
      if (psiClass == staticResolve) return false; //already imported
    }
    String text = CodeInsightBundle.message("intention.add.on.demand.static.import.text", psiClass.getQualifiedName());
    setText(text);
    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
    final PsiClass aClass = (PsiClass)refExpr.resolve();
    PsiImportStaticStatement importStaticStatement =
      JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createImportStaticStatement(aClass, "*");
    ((PsiJavaFile)file).getImportList().add(importStaticStatement);

    PsiFile[] roots = file.getPsiRoots();
    for (final PsiFile root : roots) {
      PsiElement copy = root.copy();
      final PsiManager manager = root.getManager();

      final TIntArrayList expressionToDequalifyOffsets = new TIntArrayList();
      copy.accept(new JavaRecursiveElementWalkingVisitor() {
        int delta = 0;
        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          if (isParameterizedReference(expression)) return;
          PsiExpression qualifierExpression = expression.getQualifierExpression();
          if (qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).isReferenceTo(aClass)) {
            try {
              PsiElement resolved = expression.resolve();
              if (resolved == null) return;
              int end = expression.getTextRange().getEndOffset();
              qualifierExpression.delete();
              delta += end - expression.getTextRange().getEndOffset();
              PsiElement after = expression.resolve();
              if (manager.areElementsEquivalent(after, resolved)) {
                expressionToDequalifyOffsets.add(expression.getTextRange().getStartOffset() + delta);
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
          super.visitElement(expression);
        }
      });

      expressionToDequalifyOffsets.forEachDescending(new TIntProcedure() {
        public boolean execute(int offset) {
          PsiReferenceExpression expression = PsiTreeUtil.findElementOfClassAtOffset(root, offset, PsiReferenceExpression.class, false);
          PsiExpression qualifierExpression = expression.getQualifierExpression();
          if (qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).isReferenceTo(aClass)) {
            qualifierExpression.delete();
            HighlightManager.getInstance(project)
              .addRangeHighlight(editor, expression.getTextRange().getStartOffset(), expression.getTextRange().getEndOffset(),
                                 EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES),
                                 false, null);
          }

          return true;
        }
      });
    }
  }

  private static boolean isParameterizedReference(final PsiReferenceExpression expression) {
    return expression.getParameterList() != null && expression.getParameterList().getFirstChild() != null;
  }
}
