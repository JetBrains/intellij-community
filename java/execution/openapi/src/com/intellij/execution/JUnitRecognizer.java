package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Evdokimov
 */
public abstract class JUnitRecognizer {

  public static final ExtensionPointName<JUnitRecognizer> EP_NAME = ExtensionPointName.create("com.intellij.junitRecognizer");

  public boolean isTestClass(@NotNull PsiClass aClass) {
    return false;
  }

  public boolean isTestMethod(@NotNull PsiMethod method) {
    return false;
  }
}
