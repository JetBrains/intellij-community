// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public abstract class JUnitRecognizer {
  public static final ExtensionPointName<JUnitRecognizer> EP_NAME = ExtensionPointName.create("com.intellij.junitRecognizer");

  public abstract boolean isTestAnnotated(@NotNull PsiMethod method);

  public static boolean willBeAnnotatedAfterCompilation(@NotNull PsiMethod method) {
    for (JUnitRecognizer jUnitRecognizer : EP_NAME.getIterable(null)) {
      if (jUnitRecognizer.isTestAnnotated(method)) {
        return true;
      }
    }
    return false;
  }
}
