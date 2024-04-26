// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Bas Leijdekkers
 */
public final class ParameterTypePreventsOverridingInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final String qualifiedName1 = (String)infos[0];
    final String packageName = StringUtil.getPackageName(qualifiedName1);
    final String qualifiedName2 = (String)infos[1];
    final String superPackageName = StringUtil.getPackageName(qualifiedName2);
    return InspectionGadgetsBundle.message("parameter.type.prevents.overriding.problem.descriptor", packageName, superPackageName);
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ParameterTypePreventsOverridingFix((String)infos[1]);
  }

  private static class ParameterTypePreventsOverridingFix extends PsiUpdateModCommandQuickFix {

    private final String myNewTypeText;

    ParameterTypePreventsOverridingFix(String newTypeText) {
      myNewTypeText = newTypeText;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("parameter.type.prevents.overriding.quickfix", myNewTypeText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("parameter.type.prevents.overriding.family.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiTypeElement typeElement)) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(typeElement.getProject());
      final PsiTypeElement newTypeElement = factory.createTypeElementFromText(myNewTypeText, typeElement);
      typeElement.replace(newTypeElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ParameterTypePreventsOverridingVisitor();
  }

  private static class ParameterTypePreventsOverridingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final int parameterCount = parameterList.getParametersCount();
      if (parameterCount == 0) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiClass superClass = containingClass.getSuperClass();
      if (superClass == null) {
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final String name = method.getName();
      final PsiMethod[] superMethods = superClass.findMethodsByName(name, true);
      outer: for (PsiMethod superMethod : superMethods) {
        final PsiType superReturnType = superMethod.getReturnType();
        if (superReturnType == null || !superReturnType.isAssignableFrom(returnType)) {
          continue;
        }
        final PsiParameterList superParameterList = superMethod.getParameterList();
        if (superParameterList.getParametersCount() != parameterCount) {
          continue;
        }
        final PsiParameter[] superParameters = superParameterList.getParameters();
        final Map<PsiTypeElement, PsiClassType> problemTypeElements = new HashMap<>(2);
        for (int i = 0; i < parameters.length; i++) {
          final PsiParameter parameter = parameters[i];
          final PsiParameter superParameter = superParameters[i];
          final PsiType type = parameter.getType();
          final PsiType superType = superParameter.getType();
          if (!(superType instanceof PsiClassType) || type.equals(superType)) {
            continue;
          }
          if (!(type instanceof PsiClassType) || !type.getPresentableText().equals(superType.getPresentableText())) {
            return;
          }
          final PsiTypeElement typeElement = parameter.getTypeElement();
          if (typeElement == null) {
            return;
          }
          final PsiTypeElement superParameterTypeElement = superParameter.getTypeElement();
          if (superParameterTypeElement == null) {
            continue outer;
          }
          problemTypeElements.put(typeElement, (PsiClassType)superType);
        }
        for (Map.Entry<PsiTypeElement, PsiClassType> entry : problemTypeElements.entrySet()) {
          final PsiTypeElement typeElement = entry.getKey();
          final PsiClassType type = (PsiClassType)typeElement.getType();
          final PsiClass aClass1 = type.resolve();
          if (aClass1 == null || aClass1 instanceof PsiTypeParameter) {
            return;
          }
          final PsiClassType classType = entry.getValue();
          final PsiClass aClass2 = classType.resolve();
          if (aClass2 == null || aClass2 instanceof PsiTypeParameter) {
            continue;
          }
          registerError(typeElement, type.getCanonicalText(), classType.getCanonicalText());
        }
      }
    }
  }
}
