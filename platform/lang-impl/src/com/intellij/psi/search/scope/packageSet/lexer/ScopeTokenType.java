// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.search.scope.packageSet.lexer;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ScopeTokenType extends IElementType {
  public ScopeTokenType(final @NotNull @NonNls String debugName) {
    super(debugName, Language.ANY);
  }
}