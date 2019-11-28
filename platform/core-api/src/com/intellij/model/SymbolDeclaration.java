// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;

/**
 * <pre>
 * SymbolDeclaration           d1   d2  dN
 *                               ↘  ↓  ↙
 * Symbol                           s
 * </pre>
 *
 * @see com.intellij.model
 * @see com.intellij.model.psi.PsiSymbolDeclaration
 */
public interface SymbolDeclaration {

  @NotNull
  Symbol getSymbol();
}
