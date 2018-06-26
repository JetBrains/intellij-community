// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public interface BlockingMethodChecker {
  ExtensionPointName<BlockingMethodChecker> EP_NAME = ExtensionPointName.create("com.intellij.blockingMethodChecker");

  boolean isActive(Project project);

  boolean isMethodBlocking(@NotNull PsiMethod method);
}
