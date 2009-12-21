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
package com.intellij.codeInspection.redundantCast;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.miscGenerics.GenericsInspectionToolBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RedundantCastInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.redundantCast.RedundantCastInspection");
  private final LocalQuickFix myQuickFixAction;
  private static final String DISPLAY_NAME = InspectionsBundle.message("inspection.redundant.cast.display.name");
  @NonNls private static final String SHORT_NAME = "RedundantCast";

  public RedundantCastInspection() {
    myQuickFixAction = new AcceptSuggested();
  }

  public ProblemDescriptor[] getDescriptions(PsiElement where, InspectionManager manager, boolean isOnTheFly) {
    List<PsiTypeCastExpression> redundantCasts = RedundantCastUtil.getRedundantCastsInside(where);
    if (redundantCasts.isEmpty()) return null;
    List<ProblemDescriptor> descriptions = new ArrayList<ProblemDescriptor>(redundantCasts.size());
    for (PsiTypeCastExpression redundantCast : redundantCasts) {
      ProblemDescriptor descriptor = createDescription(redundantCast, manager, isOnTheFly);
      if (descriptor != null) {
        descriptions.add(descriptor);
      }
    }
    if (descriptions.isEmpty()) return null;
    return descriptions.toArray(new ProblemDescriptor[descriptions.size()]);
  }

  private ProblemDescriptor createDescription(@NotNull PsiTypeCastExpression cast, @NotNull InspectionManager manager, boolean onTheFly) {
    PsiExpression operand = cast.getOperand();
    PsiTypeElement castType = cast.getCastType();
    if (operand == null || castType == null) return null;
    String message = InspectionsBundle.message("inspection.redundant.cast.problem.descriptor",
                                               "<code>" + operand.getText() + "</code>", "<code>#ref</code> #loc");
    return manager.createProblemDescriptor(castType, message, myQuickFixAction, ProblemHighlightType.LIKE_UNUSED_SYMBOL, onTheFly);
  }


  private static class AcceptSuggested implements LocalQuickFix {
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.cast.remove.quickfix");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (!CodeInsightUtilBase.preparePsiElementForWrite(descriptor.getPsiElement())) return;
      PsiElement castTypeElement = descriptor.getPsiElement();
      PsiTypeCastExpression cast = castTypeElement == null ? null : (PsiTypeCastExpression)castTypeElement.getParent();
      if (cast != null) {
        removeCast(cast);
      }
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }

  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.VERBOSE_GROUP_NAME;
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private static void removeCast(PsiTypeCastExpression castExpression) {
    if (castExpression == null) return;
    PsiExpression operand = castExpression.getOperand();
    if (operand instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parExpr = (PsiParenthesizedExpression)operand;
      operand = parExpr.getExpression();
    }
    if (operand == null) return;

    PsiElement toBeReplaced = castExpression;

    PsiElement parent = castExpression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      toBeReplaced = parent;
      parent = parent.getParent();
    }

    try {
      toBeReplaced.replace(operand);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
