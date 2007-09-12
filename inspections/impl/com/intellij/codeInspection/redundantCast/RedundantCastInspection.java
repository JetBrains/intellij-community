/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.redundantCast;

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

import java.util.List;

public class RedundantCastInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.redundantCast.RedundantCastInspection");
  private final LocalQuickFix myQuickFixAction;
  private static final String DISPLAY_NAME = InspectionsBundle.message("inspection.redundant.cast.display.name");
  @NonNls private static final String SHORT_NAME = "RedundantCast";

  public RedundantCastInspection() {
    myQuickFixAction = new AcceptSuggested();
  }

  public ProblemDescriptor[] getDescriptions(PsiElement where, InspectionManager manager) {
    List<PsiTypeCastExpression> redundantCasts = RedundantCastUtil.getRedundantCastsInside(where);
    if (redundantCasts.isEmpty()) return null;
    ProblemDescriptor[] descriptions = new ProblemDescriptor[redundantCasts.size()];
    for (int i = 0; i < redundantCasts.size(); i++) {
      descriptions[i] = createDescription(redundantCasts.get(i), manager);
    }
    return descriptions;
  }

  private ProblemDescriptor createDescription(PsiTypeCastExpression cast, InspectionManager manager) {
    String message = InspectionsBundle.message("inspection.redundant.cast.problem.descriptor",
                                               "<code>" + cast.getOperand().getText() + "</code>", "<code>#ref</code> #loc");
    return manager.createProblemDescriptor(cast.getCastType(), message, myQuickFixAction, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
  }


  private static class AcceptSuggested implements LocalQuickFix {
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.cast.remove.quickfix");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
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
