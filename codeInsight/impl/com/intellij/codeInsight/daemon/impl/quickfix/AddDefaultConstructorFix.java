package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

public class AddDefaultConstructorFix extends AddMethodFix {
  public AddDefaultConstructorFix(PsiClass aClass) {
    super("public " + aClass.getName() + "() {}", aClass);
    setText(QuickFixBundle.message("add.default.constructor.text", aClass.getName()));
  }


  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.default.constructor.family");
  }
}
