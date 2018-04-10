// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class StandardDataFlowRunner extends DataFlowRunner {

  public StandardDataFlowRunner() {
    this(false, null);
  }
  public StandardDataFlowRunner(boolean unknownMembersAreNullable, @Nullable PsiElement context) {
    super(unknownMembersAreNullable, context);
  }
}
