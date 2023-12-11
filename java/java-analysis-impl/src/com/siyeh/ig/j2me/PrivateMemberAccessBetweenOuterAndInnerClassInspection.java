/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PrivateMemberAccessBetweenOuterAndInnerClassInspection extends BaseInspection {

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "SyntheticAccessorCall";
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "PrivateMemberAccessBetweenOuterAndInnerClass";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message(
      "private.member.access.between.outer.and.inner.classes.problem.descriptor",
      aClass.getName());
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    if (infos.length == 1) {
      return new MakePackagePrivateFix(className, true);
    }
    final PsiMember member = (PsiMember)infos[1];
    @NonNls final String memberName;
    if (member instanceof PsiMethod) {
      memberName = member.getName() + "()";
    }
    else {
      memberName = member.getName();
    }
    @NonNls final String elementName = className + '.' + memberName;
    return new MakePackagePrivateFix(elementName, false);
  }

  private static final class MakePackagePrivateFix extends PsiUpdateModCommandQuickFix {

    private final String elementName;
    private final boolean constructor;

    private MakePackagePrivateFix(String elementName, boolean constructor) {
      this.elementName = elementName;
      this.constructor = constructor;
    }

    @Override
    @NotNull
    public String getName() {
      if (constructor) {
        return InspectionGadgetsBundle.message(
          "private.member.access.between.outer.and.inner.classes.make.constructor.package.local.quickfix",
          elementName);
      }
      return InspectionGadgetsBundle.message(
        "private.member.access.between.outer.and.inner.classes.make.local.quickfix",
        elementName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("make.package.private.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (constructor) {
        makeConstructorPackageLocal(project, element);
      }
      else {
        makeMemberPackageLocal(element);
      }
    }

    private static void makeMemberPackageLocal(PsiElement element) {
      final PsiElement parent = element.getParent();
      final PsiReferenceExpression reference =
        (PsiReferenceExpression)parent;
      final PsiModifierListOwner member =
        (PsiModifierListOwner)reference.resolve();
      if (member == null) {
        return;
      }
      final PsiModifierList modifiers = member.getModifierList();
      if (modifiers == null) {
        return;
      }
      modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
      modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
      modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
    }

    private static void makeConstructorPackageLocal(Project project, PsiElement element) {
      final PsiNewExpression newExpression =
        PsiTreeUtil.getParentOfType(element,
                                    PsiNewExpression.class);
      if (newExpression == null) {
        return;
      }
      final PsiMethod constructor =
        newExpression.resolveConstructor();
      if (constructor != null) {
        final PsiModifierList modifierList =
          constructor.getModifierList();
        modifierList.setModifierProperty(PsiModifier.PRIVATE,
                                         false);
        return;
      }
      final PsiJavaCodeReferenceElement referenceElement =
        (PsiJavaCodeReferenceElement)element;
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass aClass)) {
        return;
      }
      final PsiElementFactory elementFactory =
        JavaPsiFacade.getElementFactory(project);
      final PsiMethod newConstructor = elementFactory.createConstructor();
      final PsiModifierList modifierList =
        newConstructor.getModifierList();
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
      aClass.add(newConstructor);
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    if (FileTypeUtils.isInServerPageFile(file)) {
      // disable for jsp files IDEADEV-12957
      return false;
    }
    return !PsiUtil.isLanguageLevel11OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PrivateMemberAccessFromInnerClassVisitor();
  }

  private static class PrivateMemberAccessFromInnerClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (expression.getType() instanceof PsiArrayType) {
        return;
      }
      final PsiMethod constructor = expression.resolveMethod();
      final PsiClass aClass;
      if (constructor == null) {
        final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
        if (classReference == null) {
          return;
        }
        final PsiElement target = classReference.resolve();
        if (!(target instanceof PsiClass)) {
          return;
        }
        aClass = (PsiClass)target;
        if (aClass.isInterface() || !aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
      }
      else {
        if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        aClass = constructor.getContainingClass();
      }
      if (!isInnerClassAccess(expression, aClass)) {
        return;
      }
      registerNewExpressionError(expression, aClass);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement == null) {
        return;
      }
      final JavaResolveResult resolveResult = expression.advancedResolve(false);
      if (!resolveResult.isAccessible()) {
        return;
      }
      final PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiMethod || element instanceof PsiField)) {
        return;
      }
      final PsiMember member = (PsiMember)element;
      if (!member.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final Object value = ExpressionUtils.computeConstantExpression(expression);
      if (value != null) {
        return; // no synthetic accessor created, compile time constant will be inlined by javac
      }
      final PsiClass memberClass = member.getContainingClass();
      if (!isInnerClassAccess(expression, memberClass)) {
        return;
      }
      registerError(referenceNameElement, memberClass, member);
    }

    private static boolean isInnerClassAccess(PsiExpression reference, PsiClass targetClass) {
      final PsiClass sourceClass = ClassUtils.getContainingClass(reference);
      return sourceClass != null &&
             targetClass != null &&
             sourceClass != targetClass &&
             PsiUtil.getTopLevelClass(sourceClass) == PsiUtil.getTopLevelClass(targetClass);
    }
  }
}
