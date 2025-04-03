/*
 * Copyright 2003-2024 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ImportUtils;
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
  public @NotNull String buildErrorString(Object... infos) {
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
    public @NotNull String getFamilyName() {
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
    final String referenceName = referenceElement.getReferenceName();
    if (referenceName == null) {
      return false;
    }
    if (referenceName.equals(JavaKeywords.YIELD) && parent instanceof PsiMethodCallExpression) {
      // Qualifier required since Java 14 (JLS 3.8)
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
    final PsiElement resolvedQualifier = qualifier.resolve();
    if (!(resolvedQualifier instanceof PsiClass)) {
      return false;
    }
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(referenceElement, PsiClass.class);
    if (containingClass == null) {
      return false;
    }
    final PsiMember member = (PsiMember)target;
    if (!ImportUtils.isReferenceCorrectWithoutQualifier(referenceElement, member)) {
      return false;
    }
    final PsiClass memberClass = member.getContainingClass();
    if (member instanceof PsiMethod && memberClass != null && memberClass.isInterface() &&
        !PsiTreeUtil.isAncestor(memberClass, referenceElement, true)) {
      return false;
    }
    if (target instanceof PsiField && containingClass == memberClass) {
      final TextRange referenceElementTextRange = referenceElement.getTextRange();
      if (referenceElementTextRange == null) {
        return false;
      }
      final TextRange variableTextRange = member.getTextRange();
      if (variableTextRange == null) {
        return false;
      }
      //illegal forward ref
      if (referenceElementTextRange.getStartOffset() < variableTextRange.getEndOffset()) {
        return false;
      }
    }
    return !ImportUtils.isStaticallyImported(member, referenceElement);
  }
}