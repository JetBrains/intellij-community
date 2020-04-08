// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.redundantCast;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.miscGenerics.GenericsInspectionToolBase;
import com.intellij.codeInspection.miscGenerics.SuspiciousMethodCallUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class RedundantCastInspection extends GenericsInspectionToolBase {
  private final LocalQuickFix myQuickFixAction;
  @NonNls private static final String SHORT_NAME = "RedundantCast";

  public boolean IGNORE_SUSPICIOUS_METHOD_CALLS;


  public RedundantCastInspection() {
    myQuickFixAction = new AcceptSuggested();
  }

  @Override
  public ProblemDescriptor @Nullable [] getDescriptions(@NotNull PsiElement where, @NotNull InspectionManager manager, boolean isOnTheFly) {
    List<PsiTypeCastExpression> redundantCasts = RedundantCastUtil.getRedundantCastsInside(where);
    if (redundantCasts.isEmpty()) return null;
    List<ProblemDescriptor> descriptions = new ArrayList<>(redundantCasts.size());
    for (PsiTypeCastExpression redundantCast : redundantCasts) {
      ProblemDescriptor descriptor = createDescription(redundantCast, manager, isOnTheFly);
      if (descriptor != null) {
        descriptions.add(descriptor);
      }
    }
    if (descriptions.isEmpty()) return null;
    return descriptions.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return getDescriptions(field, manager, isOnTheFly);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (IGNORE_SUSPICIOUS_METHOD_CALLS) {
      super.writeSettings(node);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(JavaAnalysisBundle.message("ignore.casts.in.suspicious.collections.method.calls"), "IGNORE_SUSPICIOUS_METHOD_CALLS");
    return optionsPanel;
  }

  @Nullable
  private ProblemDescriptor createDescription(@NotNull PsiTypeCastExpression cast, @NotNull InspectionManager manager, boolean onTheFly) {
    PsiExpression operand = cast.getOperand();
    PsiTypeElement castType = cast.getCastType();
    if (operand == null || castType == null) return null;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(cast.getParent());
    if (parent instanceof PsiExpressionList)  {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiMethodCallExpression && IGNORE_SUSPICIOUS_METHOD_CALLS) {
        final String message = SuspiciousMethodCallUtil
          .getSuspiciousMethodCallMessage((PsiMethodCallExpression)gParent, operand, operand.getType(), true, new ArrayList<>(), 0);
        if (message != null) {
          return null;
        }
      }
    }

    String message = JavaAnalysisBundle.message("inspection.redundant.cast.problem.descriptor",
                                               "<code>" + PsiExpressionTrimRenderer.render(operand) + "</code>", "<code>#ref</code> #loc");
    return manager.createProblemDescriptor(castType, message, myQuickFixAction, ProblemHighlightType.LIKE_UNUSED_SYMBOL, onTheFly);
  }


  private static class AcceptSuggested implements LocalQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.redundant.cast.remove.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement castTypeElement = descriptor.getPsiElement();
      PsiTypeCastExpression cast = castTypeElement == null ? null : (PsiTypeCastExpression)castTypeElement.getParent();
      if (cast != null) {
        RemoveRedundantCastUtil.removeCast(cast);
      }
    }
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.verbose.or.redundant.code.constructs");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }
}
