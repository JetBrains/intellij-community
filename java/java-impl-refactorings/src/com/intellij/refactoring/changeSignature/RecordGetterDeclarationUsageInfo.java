// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;

public class RecordGetterDeclarationUsageInfo extends UsageInfo {
  public final String myNewName;
  public final String myNewType;

  public RecordGetterDeclarationUsageInfo(PsiElement element, String newName, String newType) {
    super(element);
    myNewName = newName;
    myNewType = newType;
  }
}
