// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class AbsoluteAlignmentInUserInterfaceInspection extends BaseInspection {

  private static final @NonNls Map<String, String> gridbagConstants = Map.of(
    "NORTHWEST", "FIRST_LINE_START",
    "NORTHEAST", "FIRST_LINE_END",
    "SOUTHWEST", "LAST_LINE_START",
    "SOUTHEAST", "LAST_LINE_END"
  );

  private static final @NonNls Map<String, String> borderLayoutConstants = Map.of(
    "NORTH", "PAGE_START",
    "SOUTH", "PAGE_END",
    "EAST", "LINE_END",
    "WEST", "LINE_START"
  );
  private static final @NonNls Map<String, String> flowLayoutConstants = Map.of(
    "LEFT", "LEADING",
    "RIGHT", "TRAILING"
  );
  private static final @NonNls Map<String, String> scrollPaneConstants = Map.of(
    "LOWER_LEFT_CORNER", "LOWER_LEADING_CORNER",
    "LOWER_RIGHT_CORNER", "LOWER_TRAILING_CORNER",
    "UPPER_LEFT_CORNER", "UPPER_LEADING_CORNER",
    "UPPER_RIGHT_CORNER", "UPPER_TRAILING_CORNER"
  );
  private static final @NonNls Map<String, String> boxLayoutConstants = Map.of(
    "X_AXIS", "LINE_AXIS",
    "Y_AXIS", "PAGE_AXIS"
  );

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final String className = (String)infos[0];
    final String shortClassName = className.substring(className.lastIndexOf('.') + 1);
    return InspectionGadgetsBundle.message("absolute.alignment.in.user.interface.problem.descriptor", shortClassName);
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new AbsoluteAlignmentInUserInterfaceFix((String)infos[0], (String)infos[1]);
  }

  private static class AbsoluteAlignmentInUserInterfaceFix extends PsiUpdateModCommandQuickFix {

    private final String myClassName;
    private final String myReplacement;

    AbsoluteAlignmentInUserInterfaceFix(String className, String replacement) {
      myClassName = className;
      myReplacement = replacement;
    }

    @Override
    public @NotNull String getName() {
      final String shortClassName = myClassName.substring(myClassName.lastIndexOf('.') + 1);
      return CommonQuickFixBundle.message("fix.replace.with.x", shortClassName + "." + myReplacement);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("absolute.alignment.in.user.interface.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      PsiReplacementUtil.replaceExpression(referenceExpression, myClassName + '.' + myReplacement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AbsoluteAlignmentInUserInterfaceVisitor();
  }

  private static class AbsoluteAlignmentInUserInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement ==  null) {
        return;
      }
      final String referenceName = expression.getReferenceName();
      final String className;
      String value;
      if ((value = gridbagConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "java.awt.GridBagConstraints");
      } else if ((value = borderLayoutConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "java.awt.BorderLayout", "java.awt.GridBagConstraints");
      } else if ((value = flowLayoutConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "java.awt.FlowLayout");
      } else if ((value = scrollPaneConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "javax.swing.ScrollPaneConstants");
      } else if ((value = boxLayoutConstants.get(referenceName)) != null) {
        className = checkExpression(expression, "javax.swing.BoxLayout");
      } else {
        return;
      }
      if (className == null) {
        return;
      }
      registerError(referenceNameElement, className, value);
    }

    private static String checkExpression(PsiReferenceExpression expression, String... classNames) {
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiField field)) {
        return null;
      }
      final PsiClass containingClass = field.getContainingClass();
      for (String className : classNames) {
        if (InheritanceUtil.isInheritor(containingClass, className)) {
          return className;
        }
      }
      return null;
    }
  }
}
