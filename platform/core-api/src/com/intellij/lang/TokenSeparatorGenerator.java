// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import org.jetbrains.annotations.Nullable;

public interface TokenSeparatorGenerator {
  @Nullable
  ASTNode generateWhitespaceBetweenTokens(ASTNode left, ASTNode right);
}
