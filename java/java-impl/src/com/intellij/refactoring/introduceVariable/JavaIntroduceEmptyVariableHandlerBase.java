// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public interface JavaIntroduceEmptyVariableHandlerBase {
  void invoke(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiType type);
}
