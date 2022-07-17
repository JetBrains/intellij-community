// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.symbol;

import com.intellij.lang.documentation.DocumentationTarget;
import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

/**
 * @see SymbolDocumentationTargetFactory
 */
@Experimental
public interface DocumentationSymbol extends Symbol {

  @Override
  @NotNull Pointer<? extends DocumentationSymbol> createPointer();

  @NotNull DocumentationTarget getDocumentationTarget();
}
