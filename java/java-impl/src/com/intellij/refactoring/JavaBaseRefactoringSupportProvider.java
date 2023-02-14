// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import com.intellij.refactoring.typeMigration.ChangeTypeSignatureHandlerBase;
import org.jetbrains.annotations.NotNull;

public abstract class JavaBaseRefactoringSupportProvider extends RefactoringSupportProvider {
  @Override
  public abstract @NotNull JavaIntroduceVariableHandlerBase getIntroduceVariableHandler();

  public abstract @NotNull ChangeTypeSignatureHandlerBase getChangeTypeSignatureHandler();
}
