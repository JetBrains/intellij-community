// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TokenSeparatorGenerator {
  @Nullable
  ASTNode generateWhitespaceBetweenTokens(@Nullable ASTNode left, @NotNull ASTNode right);
}
