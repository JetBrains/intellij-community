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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public sealed class CreateGetterOrSetterFix extends PsiUpdateModCommandAction<PsiField> {
  private final Create myCreate;
  private final String myPropertyName;

  private enum Create { GETTER , SETTER , GETTER_SETTER }
  
  public static final class CreateGetterFix extends CreateGetterOrSetterFix {
    public CreateGetterFix(@NotNull PsiField field) {
      super(Create.GETTER, field);
    }
  }

  public static final class CreateSetterFix extends CreateGetterOrSetterFix {
    public CreateSetterFix(@NotNull PsiField field) {
      super(Create.SETTER, field);
    }
  }

  public static final class CreateGetterAndSetterFix extends CreateGetterOrSetterFix {
    public CreateGetterAndSetterFix(@NotNull PsiField field) {
      super(Create.GETTER_SETTER, field);
    }
  }
  
  private CreateGetterOrSetterFix(Create create, @NotNull PsiField field) {
    super(field);
    myCreate = create;
    myPropertyName = PropertyUtilBase.suggestPropertyName(field);
  }

  private boolean createGetter() {
    return myCreate != Create.SETTER;
  }

  private boolean createSetter() {
    return myCreate != Create.GETTER;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.accessor.for.unused.field.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiField field) {
    final PsiClass aClass = field.getContainingClass();
    if (aClass == null) return null;

    if (createGetter()) {
      if (isStaticFinal(field) || PropertyUtilBase.findPropertyGetter(aClass, myPropertyName, isStatic(field), false) != null) {
        return null;
      }
    }
    if (createSetter()) {
      if (isFinal(field) || PropertyUtilBase.findPropertySetter(aClass, myPropertyName, isStatic(field), false) != null) {
        return null;
      }
    }

    final String what = switch (myCreate) {
      case GETTER_SETTER -> "create.getter.and.setter.for.field";
      case GETTER -> "create.getter.for.field";
      case SETTER -> "create.setter.for.field";
    };
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
    if (createGetter()) {
      Collections.addAll(methods, GetterSetterPrototypeProvider.generateGetterSetters(field, true));
    }
    if (createSetter()) {
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
