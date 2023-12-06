// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation;

import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;

/**
 * @see SymbolDocumentationTargetProvider
 */
@Experimental
@OverrideOnly
public interface DocumentationSymbol extends Symbol {

  @Override
  @NotNull Pointer<? extends DocumentationSymbol> createPointer();

  @NotNull DocumentationTarget getDocumentationTarget();
}
