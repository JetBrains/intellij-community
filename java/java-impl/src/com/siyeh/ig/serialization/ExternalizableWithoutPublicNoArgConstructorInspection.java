// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.impl.quickfix.AddDefaultConstructorFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class ExternalizableWithoutPublicNoArgConstructorInspection extends BaseInspection {

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiMethod constructor = (PsiMethod)infos[1];
    if (constructor == null) {
      final PsiClass aClass = (PsiClass)infos[0];
      if (aClass instanceof PsiAnonymousClass) {
        // can't create constructor for anonymous class
        return null;
      }
      return LocalQuickFix.from(new AddDefaultConstructorFix(aClass, PsiModifier.PUBLIC));
    }
    else {
      return new MakeConstructorPublicFix();
    }
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("externalizable.without.public.no.arg.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExternalizableWithoutPublicNoArgConstructorVisitor();
  }

  @Nullable
  private static PsiMethod getNoArgConstructor(PsiMethod[] constructors) {
    for (PsiMethod constructor : constructors) {
      final PsiParameterList parameterList = constructor.getParameterList();
      if (parameterList.isEmpty()) {
        return constructor;
      }
    }
    return null;
  }

  private static class MakeConstructorPublicFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("make.constructor.public");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement classNameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiClass aClass = (PsiClass)classNameIdentifier.getParent();
      if (aClass == null) {
        return;
      }
      final PsiMethod constructor = getNoArgConstructor(aClass.getConstructors());
      if (constructor == null) {
        return;
      }
      constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
    }
  }

  private static class ExternalizableWithoutPublicNoArgConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType() || aClass.isRecord() || aClass instanceof PsiTypeParameter) {
        return;
      }
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (!isExternalizable(aClass)) {
        return;
      }
      final PsiMethod[] constructors = aClass.getConstructors();
      final PsiMethod constructor = getNoArgConstructor(constructors);
      if (constructor == null) {
        if (aClass.hasModifierProperty(PsiModifier.PUBLIC) && constructors.length == 0) {
          return;
        }
      } else {
        if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
          return;
        }
      }
      if (SerializationUtils.hasWriteReplace(aClass)) {
        return;
      }
      registerClassError(aClass, aClass, constructor);
    }

    private static boolean isExternalizable(PsiClass aClass) {
      final PsiClass externalizableClass = ClassUtils.findClass("java.io.Externalizable", aClass);
      return externalizableClass != null && aClass.isInheritor(externalizableClass, true);
    }
  }
}