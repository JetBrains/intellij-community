// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation.impl

import com.intellij.model.presentation.SymbolPresentation
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class DefaultSymbolPresentation(
  private val icon: Icon?,
  @Nls private val typeString: String,
  @Nls private val shortNameString: String,
  @Nls private val longNameString: String? = shortNameString
) : SymbolPresentation {
  override fun getIcon(): Icon? = icon
  override fun getShortNameString(): String = shortNameString
  override fun getShortDescription(): String = "$typeString '$shortNameString'"
  override fun getLongDescription(): String = "$typeString '$longNameString'"
}
