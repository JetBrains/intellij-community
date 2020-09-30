// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.documentation.impl

import com.intellij.model.Symbol
import com.intellij.model.presentation.SymbolPresentationService
import org.jetbrains.annotations.Nls

/**
 * Entry point for obtaining Symbol documentation
 */
@Nls(capitalization = Nls.Capitalization.Sentence)
fun getSymbolDocumentation(symbol: Symbol): String {
  // TODO extension
  return SymbolPresentationService.getLongDescription(symbol) // derive from presentation
}
