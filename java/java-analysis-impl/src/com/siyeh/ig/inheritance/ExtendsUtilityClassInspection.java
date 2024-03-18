// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ExtendsUtilityClassInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreUtilityClasses = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass superClass = (PsiClass)infos[0];
    final String superClassName = superClass.getName();
    return InspectionGadgetsBundle.message("class.extends.utility.class.problem.descriptor", superClassName);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreUtilityClasses", InspectionGadgetsBundle.message("class.extends.utility.class.ignore.utility.class.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassExtendsUtilityClassVisitor();
  }

  private class ClassExtendsUtilityClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      final PsiClass superClass = aClass.getSuperClass();
      if (superClass == null) {
        return;
      }
      if (superClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (!UtilityClassUtil.isUtilityClass(superClass)) {
        return;
      }
      if (ignoreUtilityClasses && UtilityClassUtil.isUtilityClass(aClass, false, false)) {
        return;
      }
      registerClassError(aClass, superClass);
    }
  }
}