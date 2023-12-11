/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessarilyQualifiedStaticUsageInspection extends BaseInspection implements CleanupLocalInspectionTool{

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreStaticFieldAccesses = false;

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreStaticMethodCalls = false;

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreStaticAccessFromStaticContext = false;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiJavaCodeReferenceElement element = (PsiJavaCodeReferenceElement)infos[0];
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiMethodCallExpression) {
      return InspectionGadgetsBundle.message("unnecessarily.qualified.static.usage.problem.descriptor", element.getText());
    }
    else {
      return InspectionGadgetsBundle.message("unnecessarily.qualified.static.usage.problem.descriptor1", element.getText());
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreStaticFieldAccesses", InspectionGadgetsBundle.message("unnecessarily.qualified.static.usage.ignore.field.option")),
      checkbox("m_ignoreStaticMethodCalls", InspectionGadgetsBundle.message("unnecessarily.qualified.static.usage.ignore.method.option")),
      checkbox("m_ignoreStaticAccessFromStaticContext", InspectionGadgetsBundle.message("only.report.qualified.static.usages.option")));
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new UnnecessarilyQualifiedStaticUsageFix();
  }

  private static class UnnecessarilyQualifiedStaticUsageFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.qualifier.for.this.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      startElement.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarilyQualifiedStaticUsageVisitor();
  }

  private class UnnecessarilyQualifiedStaticUsageVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement qualifier = reference.getQualifier();
      if (qualifier == null) {
        return;
      }
      if (!isUnnecessarilyQualifiedAccess(reference, m_ignoreStaticAccessFromStaticContext, m_ignoreStaticFieldAccesses, m_ignoreStaticMethodCalls)) {
        return;
      }
      registerError(qualifier, reference);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }
  }

  public static boolean isUnnecessarilyQualifiedAccess(@NotNull PsiJavaCodeReferenceElement referenceElement,
                                                       boolean ignoreStaticAccessFromStaticContext,
                                                       boolean ignoreStaticFieldAccesses,
                                                       boolean ignoreStaticMethodCalls) {
    if (referenceElement instanceof PsiMethodReferenceExpression) {
      return false;
    }
    final PsiElement parent = referenceElement.getParent();
    if (parent instanceof PsiImportStatementBase) {
      return false;
    }
    final PsiElement qualifierElement = referenceElement.getQualifier();
    if (!(qualifierElement instanceof PsiJavaCodeReferenceElement qualifier)) {
      return false;
    }
    if (GenericsUtil.isGenericReference(referenceElement, qualifier)) {
      return false;
    }
    final PsiElement target = referenceElement.resolve();
    if ((!(target instanceof PsiField) || ignoreStaticFieldAccesses) && (!(target instanceof PsiMethod) || ignoreStaticMethodCalls)) {
      return false;
    }
    if (ignoreStaticAccessFromStaticContext) {
      final PsiMember containingMember = PsiTreeUtil.getParentOfType(referenceElement, PsiMember.class);
      if (containingMember != null && !containingMember.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
    }
    final String referenceName = referenceElement.getReferenceName();
    if (referenceName == null) {
      return false;
    }
    final PsiElement resolvedQualifier = qualifier.resolve();
    if (!(resolvedQualifier instanceof PsiClass qualifyingClass)) {
      return false;
    }
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(referenceElement, PsiClass.class);
    if (containingClass == null || !PsiTreeUtil.isAncestor(qualifyingClass, containingClass, false)) {
      return false;
    }
    final Project project = referenceElement.getProject();
    final JavaPsiFacade manager = JavaPsiFacade.getInstance(project);
    final PsiResolveHelper resolveHelper = manager.getResolveHelper();
    final PsiMember member = (PsiMember)target;
    final PsiClass memberClass;
    if (target instanceof PsiField) {
      final PsiVariable variable = resolveHelper.resolveReferencedVariable(referenceName, referenceElement);
      if (variable == null || !variable.equals(member)) {
        return false;
      }
      final TextRange referenceElementTextRange = referenceElement.getTextRange();
      if (referenceElementTextRange == null) {
        return false;
      }
      final TextRange variableTextRange = variable.getTextRange();
      if (variableTextRange == null) {
        return false;
      }
      //illegal forward ref
      if (referenceElementTextRange.getStartOffset() < variableTextRange.getEndOffset()) {
        return false;
      }
      final PsiMember memberVariable = (PsiMember)variable;
      memberClass = memberVariable.getContainingClass();
    }
    else if (target instanceof PsiClass) {
      final PsiClass aClass = resolveHelper.resolveReferencedClass(referenceName, referenceElement);
      if (aClass == null || !aClass.equals(member)) {
        return false;
      }
      memberClass = aClass.getContainingClass();
    }
    else {
      return isMethodAccessibleWithoutQualifier(referenceElement, qualifyingClass);
    }
    return resolvedQualifier.equals(memberClass);
  }

  private static boolean isMethodAccessibleWithoutQualifier(PsiJavaCodeReferenceElement referenceElement, PsiClass qualifyingClass) {
    final String referenceName = referenceElement.getReferenceName();
    if (referenceName == null) {
      return false;
    }
    PsiClass containingClass = ClassUtils.getContainingClass(referenceElement);
    while (containingClass != null) {
      final PsiMethod[] methods = containingClass.findMethodsByName(referenceName, true);
      for (final PsiMethod method : methods) {
        final String name = method.getName();
        if (referenceName.equals(name)) {
          return containingClass.equals(qualifyingClass);
        }
      }
      containingClass = ClassUtils.getContainingClass(containingClass);
    }
    return false;
  }
}