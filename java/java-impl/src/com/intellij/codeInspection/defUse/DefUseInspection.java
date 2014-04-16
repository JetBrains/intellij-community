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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.defUse;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableFix;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DefUseInspection extends DefUseInspectionBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defUse.DefUseInspection");

  @Override
  protected LocalQuickFix createRemoveInitializerFix() {
    return new RemoveInitializerFix();
  }

  @Override
  protected LocalQuickFix createRemoveAssignmentFix() {
    return new RemoveAssignmentFix();
  }
  
  public static class RemoveAssignmentFix extends RemoveInitializerFix {
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.unused.assignment.remove.assignment.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement left = descriptor.getPsiElement();
      if (!(left instanceof PsiReferenceExpression)) return;
      final PsiElement parent = left.getParent();
      if (!(parent instanceof PsiAssignmentExpression)) return;
      final PsiExpression rExpression = ((PsiAssignmentExpression)parent).getRExpression();
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiExpression && rExpression != null) {
        if (!FileModificationService.getInstance().prepareFileForWrite(gParent.getContainingFile())) return;
        if (gParent instanceof PsiParenthesizedExpression) {
          gParent.replace(rExpression);
        } else {
          parent.replace(rExpression);
        }
        return;
      }

      final PsiElement resolve = ((PsiReferenceExpression)left).resolve();
      if (!(resolve instanceof PsiVariable)) return;
      sideEffectAwareRemove(project, rExpression, parent, (PsiVariable)resolve);
    }
  }
  private static class RemoveInitializerFix implements LocalQuickFix {
    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.unused.assignment.remove.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiInitializer = descriptor.getPsiElement();
      if (!(psiInitializer instanceof PsiExpression)) return;
      if (!(psiInitializer.getParent() instanceof PsiVariable)) return;

      final PsiVariable variable = (PsiVariable)psiInitializer.getParent();
      sideEffectAwareRemove(project, psiInitializer, psiInitializer, variable);
    }

    protected void sideEffectAwareRemove(Project project, PsiElement psiInitializer, PsiElement elementToDelete, PsiVariable variable) {
      if (!FileModificationService.getInstance().prepareFileForWrite(elementToDelete.getContainingFile())) return;

      final PsiElement declaration = variable.getParent();
      final List<PsiElement> sideEffects = new ArrayList<PsiElement>();
      boolean hasSideEffects = RemoveUnusedVariableUtil.checkSideEffects(psiInitializer, variable, sideEffects);
      int res = RemoveUnusedVariableUtil.DELETE_ALL;
      if (hasSideEffects) {
        hasSideEffects = PsiUtil.isStatement(psiInitializer);
        res = RemoveUnusedVariableFix.showSideEffectsWarning(sideEffects, variable,
                                                             FileEditorManager.getInstance(project).getSelectedTextEditor(),
                                                             hasSideEffects, sideEffects.get(0).getText(),
                                                             variable.getTypeElement().getText() +
                                                             " " +
                                                             variable.getName() +
                                                             ";<br>" +
                                                             PsiExpressionTrimRenderer
                                                               .render((PsiExpression)psiInitializer)
        );
      }
      try {
        if (res == RemoveUnusedVariableUtil.DELETE_ALL) {
          elementToDelete.delete();
        }
        else if (res == RemoveUnusedVariableUtil.MAKE_STATEMENT) {
          final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
          final PsiStatement statementFromText = factory.createStatementFromText(psiInitializer.getText() + ";", null);
          final PsiElement parent = elementToDelete.getParent();
          if (parent instanceof PsiExpressionStatement) {
            parent.replace(statementFromText);
          } else {
            declaration.getParent().addAfter(statementFromText, declaration);
            elementToDelete.delete();
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }
}
