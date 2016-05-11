/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.redundantCast;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.miscGenerics.GenericsInspectionToolBase;
import com.intellij.codeInspection.miscGenerics.SuspiciousMethodCallUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.util.containers.IntArrayList;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 * Date: Dec 24, 2001
 */
public class RedundantCastInspection extends GenericsInspectionToolBase {
  private final LocalQuickFix myQuickFixAction;
  private static final String DISPLAY_NAME = InspectionsBundle.message("inspection.redundant.cast.display.name");
  @NonNls private static final String SHORT_NAME = "RedundantCast";

  public boolean IGNORE_ANNOTATED_METHODS;
  public boolean IGNORE_SUSPICIOUS_METHOD_CALLS;


  public RedundantCastInspection() {
    myQuickFixAction = new AcceptSuggested();
  }

  @Override
  @Nullable
  public ProblemDescriptor[] getDescriptions(@NotNull PsiElement where, @NotNull InspectionManager manager, boolean isOnTheFly) {
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

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (IGNORE_ANNOTATED_METHODS || IGNORE_SUSPICIOUS_METHOD_CALLS) {
      super.writeSettings(node);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox("Ignore casts in suspicious collections method calls", "IGNORE_SUSPICIOUS_METHOD_CALLS");
    optionsPanel.addCheckbox("Ignore casts to invoke @NotNull method which overrides @Nullable", "IGNORE_ANNOTATED_METHODS");
    return optionsPanel;
  }

  @Nullable
  private ProblemDescriptor createDescription(@NotNull PsiTypeCastExpression cast, @NotNull InspectionManager manager, boolean onTheFly) {
    PsiExpression operand = cast.getOperand();
    PsiTypeElement castType = cast.getCastType();
    if (operand == null || castType == null) return null;
    PsiElement parent = cast.getParent();
    while (parent instanceof PsiParenthesizedExpression){
      parent = parent.getParent();
    }
    if (parent instanceof PsiReferenceExpression) {
      if (IGNORE_ANNOTATED_METHODS) {
        final PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiMethodCallExpression) {
          final PsiMethod psiMethod = ((PsiMethodCallExpression)gParent).resolveMethod();
          if (psiMethod != null && NullableNotNullManager.isNotNull(psiMethod)) {
            final PsiClass superClass = PsiUtil.resolveClassInType(operand.getType());
            final PsiClass containingClass = psiMethod.getContainingClass();
            if (containingClass != null && superClass != null && containingClass.isInheritor(superClass, true)) {
              for (PsiMethod method : psiMethod.findSuperMethods(superClass)) {
                if (NullableNotNullManager.isNullable(method)) {
                  return null;
                }
              }
            }
          }
        }
      }
    } else if (parent instanceof PsiExpressionList)  {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiMethodCallExpression && IGNORE_SUSPICIOUS_METHOD_CALLS) {
        final String message = SuspiciousMethodCallUtil
          .getSuspiciousMethodCallMessage((PsiMethodCallExpression)gParent, operand, operand.getType(), true, new ArrayList<PsiMethod>(),
                                          new IntArrayList());
        if (message != null) {
          return null;
        }
      }
    }

    String message = InspectionsBundle.message("inspection.redundant.cast.problem.descriptor",
                                               "<code>" + operand.getText() + "</code>", "<code>#ref</code> #loc");
    return manager.createProblemDescriptor(castType, message, myQuickFixAction, ProblemHighlightType.LIKE_UNUSED_SYMBOL, onTheFly);
  }


  private static class AcceptSuggested implements LocalQuickFix {
    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.cast.remove.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.getPsiElement())) return;
      PsiElement castTypeElement = descriptor.getPsiElement();
      PsiTypeCastExpression cast = castTypeElement == null ? null : (PsiTypeCastExpression)castTypeElement.getParent();
      if (cast != null) {
        RedundantCastUtil.removeCast(cast);
      }
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.VERBOSE_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }
}
