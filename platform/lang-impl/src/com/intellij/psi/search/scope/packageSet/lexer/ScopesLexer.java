// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.search.scope.packageSet.lexer;

import com.intellij.lexer.FlexAdapter;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class ScopesLexer extends FlexAdapter {
  public ScopesLexer() {
    super(new _ScopesLexer());
  }
}
