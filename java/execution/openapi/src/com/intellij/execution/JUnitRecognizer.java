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

  public abstract boolean isTestAnnotated(@NotNull PsiMethod method);

  public static boolean willBeAnnotatedAfterCompilation(@NotNull PsiMethod method) {
    for (JUnitRecognizer jUnitRecognizer : EP_NAME.getExtensions()) {
      if (jUnitRecognizer.isTestAnnotated(method)) {
        return true;
      }
    }

    return false;
  }

}
