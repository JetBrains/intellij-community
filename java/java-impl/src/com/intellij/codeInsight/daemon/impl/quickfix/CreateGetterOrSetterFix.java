// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreateGetterOrSetterFix extends PsiUpdateModCommandAction<PsiField> {
  private final boolean myCreateGetter;
  private final boolean myCreateSetter;
  private final String myPropertyName;
  
  public static class CreateGetterFix extends CreateGetterOrSetterFix {
    public CreateGetterFix(@NotNull PsiField field) {
      super(true, false, field);
    }
  }

  public static class CreateSetterFix extends CreateGetterOrSetterFix {
    public CreateSetterFix(@NotNull PsiField field) {
      super(false, true, field);
    }
  }

  public static class CreateGetterAndSetterFix extends CreateGetterOrSetterFix {
    public CreateGetterAndSetterFix(@NotNull PsiField field) {
      super(true, true, field);
    }
  }
  
  private CreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @NotNull PsiField field) {
    super(field);
    myCreateGetter = createGetter;
    myCreateSetter = createSetter;
    myPropertyName = PropertyUtilBase.suggestPropertyName(field);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.accessor.for.unused.field.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiField field) {
    final PsiClass aClass = field.getContainingClass();
    if (aClass == null) {
      return null;
    }

    if (myCreateGetter){
      if (isStaticFinal(field) || PropertyUtilBase.findPropertyGetter(aClass, myPropertyName, isStatic(field), false) != null){
        return null;
      }
    }

    if (myCreateSetter){
      if(isFinal(field) || PropertyUtilBase.findPropertySetter(aClass, myPropertyName, isStatic(field), false) != null){
        return null;
      }
    }

    final @NonNls String what;
    if (myCreateGetter && myCreateSetter) {
      what = "create.getter.and.setter.for.field";
    }
    else if (myCreateGetter) {
      what = "create.getter.for.field";
    }
    else if (myCreateSetter) {
      what = "create.setter.for.field";
    }
    else {
      throw new IllegalStateException("Either createGetter or createSetter must be true");
    }
    return Presentation.of(QuickFixBundle.message(what, field.getName()));
  }

  private static boolean isFinal(@NotNull PsiField field){
    return field.hasModifierProperty(PsiModifier.FINAL);
  }

  private static boolean isStatic(@NotNull PsiField field){
    return field.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isStaticFinal(@NotNull PsiField field){
    return isStatic(field) && isFinal(field);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiField field, @NotNull ModPsiUpdater updater) {
    PsiClass aClass = field.getContainingClass();
    final List<PsiMethod> methods = new ArrayList<>();
    if (myCreateGetter) {
      Collections.addAll(methods, GetterSetterPrototypeProvider.generateGetterSetters(field, true));
    }
    if (myCreateSetter) {
      Collections.addAll(methods, GetterSetterPrototypeProvider.generateGetterSetters(field, false));
    }
    assert aClass != null;
    final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(aClass.getProject());
    for (PsiMethod method : methods) {
      final PsiElement newMember = GenerateMembersUtil.insert(aClass, method, null, true);
      manager.shortenClassReferences(newMember);
    }
  }
}
