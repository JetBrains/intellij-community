// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddDefaultConstructorFix extends AddMethodFix {
  @IntentionName private final String myText;

  public AddDefaultConstructorFix(PsiClass aClass) {
    this(aClass, PsiUtil.getSuitableModifierForMember(aClass, true));
  }

  public AddDefaultConstructorFix(PsiClass aClass, @NotNull @PsiModifier.ModifierConstant final String modifier) {
    super(generateConstructor(aClass.getName(), modifier), aClass);
    myText = QuickFixBundle.message("add.default.constructor.text", VisibilityUtil.toPresentableText(modifier), aClass.getName());
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass myClass) {
    return super.getPresentation(context, myClass) == null ? null : Presentation.of(myText);
  }

  private static String generateConstructor(final String className, @PsiModifier.ModifierConstant final String modifier) {
    if (modifier.equals(PsiModifier.PACKAGE_LOCAL)) {
      return className + "() {}";
    }
    return modifier + " " + className + "() {}";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.default.constructor.family");
  }

  /**
   * @param psiClass PsiClass to add default constructor to
   * @return added constructor
   */
  public static @NotNull PsiMethod addDefaultConstructor(@NotNull PsiClass psiClass) {
    return new AddDefaultConstructorFix(psiClass).createMethod(psiClass);
  }
}
