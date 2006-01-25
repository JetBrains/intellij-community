package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiClass;

public class AddDefaultConstructorFix extends AddMethodFix {
  public AddDefaultConstructorFix(PsiClass aClass) {
    super("public " + aClass.getName() + "() {}", aClass);
    setText(QuickFixBundle.message("add.default.constructor.text", aClass.getName()));
  }

  public String getFamilyName() {
    return QuickFixBundle.message("add.default.constructor.family");
  }
}
