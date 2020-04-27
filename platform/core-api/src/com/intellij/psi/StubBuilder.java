// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public interface StubBuilder {
  StubElement buildStubTree(@NotNull PsiFile file);

  boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node);
}