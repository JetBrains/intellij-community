// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;


public interface RefactoringHelper<T> {
  ExtensionPointName<RefactoringHelper> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.helper");

  T prepareOperation(UsageInfo @NotNull [] usages);
  void performOperation(@NotNull Project project, T operationData);
}
