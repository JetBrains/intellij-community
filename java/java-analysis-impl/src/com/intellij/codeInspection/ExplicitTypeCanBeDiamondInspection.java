/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 1/28/11
 */
public class ExplicitTypeCanBeDiamondInspection extends BaseJavaBatchLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance("#" + ExplicitTypeCanBeDiamondInspection.class.getName());

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Explicit type can be replaced with <>";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Convert2Diamond";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        if (PsiDiamondTypeUtil.canCollapseToDiamond(expression, expression, null)) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
          LOG.assertTrue(classReference != null);
          final PsiReferenceParameterList parameterList = classReference.getParameterList();
          LOG.assertTrue(parameterList != null);
          final PsiElement firstChild = parameterList.getFirstChild();
          final PsiElement lastChild = parameterList.getLastChild();
          final TextRange range = new TextRange(firstChild != null && firstChild.getNode().getElementType() == JavaTokenType.LT ? 1 : 0,
                                                parameterList.getTextLength() - (lastChild != null && lastChild.getNode().getElementType() == JavaTokenType.GT ? 1 : 0));
          holder.registerProblem(parameterList, "Explicit type argument #ref #loc can be replaced with <>", ProblemHighlightType.LIKE_UNUSED_SYMBOL, range, new ReplaceWithDiamondFix());
        }
      }
    };
  }

  private static class ReplaceWithDiamondFix implements LocalQuickFix, HighPriorityAction {
    @NotNull
    @Override
    public String getName() {
      return "Replace with <>";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      final PsiNewExpression newExpression =
        PsiTreeUtil.getParentOfType(PsiDiamondTypeUtil.replaceExplicitWithDiamond(element), PsiNewExpression.class);
      if (newExpression != null) {
        CodeStyleManager.getInstance(project).reformat(newExpression);
      }
    }
  }
}
