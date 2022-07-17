// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

public interface JavaIntroduceVariableHandlerBase extends RefactoringActionHandler, ContextAwareActionHandler {
  void invoke(@NotNull Project project, Editor editor, PsiExpression expression);
}
