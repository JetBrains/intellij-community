// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class DoubleBraceInitializationInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("double.brace.initialization.display.name");
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final PsiElement parent = PsiTreeUtil.skipParentsOfType(aClass, PsiNewExpression.class, PsiParenthesizedExpression.class);
    if (!(parent instanceof PsiVariable) && !(parent instanceof PsiAssignmentExpression)) {
      return null;
    }
    PsiElement anchor = PsiTreeUtil.getParentOfType(aClass, PsiMember.class, PsiStatement.class);
    if (anchor instanceof PsiMember) {
      PsiClass surroundingClass = ((PsiMember)anchor).getContainingClass();
      if (surroundingClass == null || surroundingClass.isInterface()) return null;
    }
    return new DoubleBraceInitializationFix();
  }

  private static class DoubleBraceInitializationFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("double.brace.initialization.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiElement element = startElement.getParent();
      if (!(element instanceof PsiAnonymousClass aClass)) {
        return;
      }
      final PsiElement parent = aClass.getParent();
      if (!(parent instanceof PsiNewExpression newExpression)) {
        return;
      }
      final PsiElement ancestor = PsiTreeUtil.skipParentsOfType(newExpression, PsiParenthesizedExpression.class);
      final String qualifierText;
      if (ancestor instanceof PsiVariable) {
        qualifierText = ((PsiVariable)ancestor).getName();
      }
      else if (ancestor instanceof PsiAssignmentExpression assignmentExpression) {
        final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
        if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
          return;
        }
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        qualifierText = referenceExpression.getText();
      }
      else {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiJavaCodeReferenceElement baseClassReference = aClass.getBaseClassReference();
      final PsiElement baseClassTarget = baseClassReference.resolve();
      if (!(baseClassTarget instanceof PsiClass)) {
        return;
      }
      final PsiExpressionList argumentList = aClass.getArgumentList();
      if (argumentList == null) {
        return;
      }
      qualifyReferences(aClass, (PsiClass) baseClassTarget, qualifierText);
      final PsiClassInitializer initializer = aClass.getInitializers()[0];
      final PsiCodeBlock body = initializer.getBody();
      PsiElement child = body.getLastBodyElement();
      final PsiElement stop = body.getFirstBodyElement();
      final PsiElement anchor = PsiTreeUtil.getParentOfType(aClass, PsiMember.class, PsiStatement.class);
      if (anchor == null) {
        return;
      }
      if (anchor instanceof PsiMember member) {
        final PsiClassInitializer newInitializer = factory.createClassInitializer();
        if (member.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiModifierList modifierList = newInitializer.getModifierList();
          if (modifierList != null) {
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
          }
        }
        final PsiCodeBlock initializerBody = newInitializer.getBody();
        while (child != null && !child.equals(stop)) {
          initializerBody.add(child);
          child = child.getPrevSibling();
        }
        member.getParent().addAfter(newInitializer, member);
      }
      else {
        final PsiElement container = anchor.getParent();
        while (child != null && !child.equals(stop)) {
          container.addAfter(child, anchor);
          child = child.getPrevSibling();
        }
      }
      final PsiExpression newNewExpression =
        factory.createExpressionFromText("new " + baseClassReference.getText() + argumentList.getText(), aClass);
      newExpression.replace(newNewExpression);
    }

    private static void qualifyReferences(PsiElement element, final PsiClass target, final String qualifierText) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
      element.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          if (expression.getQualifierExpression() != null) {
            return;
          }
          final PsiElement expressionTarget = expression.resolve();
          if (!(expressionTarget instanceof PsiMember member)) {
            return;
          }
          final PsiClass containingClass = member.getContainingClass();
          if (!InheritanceUtil.isInheritorOrSelf(target, containingClass, true)) {
            return;
          }
          final PsiExpression newExpression = factory.createExpressionFromText(qualifierText + '.' + expression.getText(), expression);
          expression.replace(newExpression);
        }
      });
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DoubleBraceInitializationVisitor();
  }

  private static class DoubleBraceInitializationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
      super.visitAnonymousClass(aClass);
      if (ClassUtils.getDoubleBraceInitializer(aClass) == null) return;
      registerClassError(aClass, aClass);
    }
  }
}
