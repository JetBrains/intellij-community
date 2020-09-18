// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.convertToRecord;

import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates the method which will be renamed by its new name as the record specifies accessors naming.
 */
class RenameMethodUsageInfo extends UsageInfo implements ConvertToRecordUsageInfo {
  final PsiMethod myMethod;
  final String myNewName;

  RenameMethodUsageInfo(@NotNull PsiMethod method, @NotNull String name) {
    super(method);
    myMethod = method;
    myNewName = name;
  }
}
