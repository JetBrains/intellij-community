// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;

public class AddDefaultConstructorFix extends AddMethodFix {
  public AddDefaultConstructorFix(PsiClass aClass) {
    this(aClass, PsiUtil.getSuitableModifierForMember(aClass, true));
  }

  public AddDefaultConstructorFix(PsiClass aClass, @NotNull @PsiModifier.ModifierConstant final String modifier) {
    super(generateConstructor(aClass.getName(), modifier), aClass);
    setText(QuickFixBundle.message("add.default.constructor.text", VisibilityUtil.toPresentableText(modifier), aClass.getName()));
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
}
