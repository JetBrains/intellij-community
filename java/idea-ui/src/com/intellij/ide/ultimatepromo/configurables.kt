// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ultimatepromo

import com.intellij.icons.AllIcons
import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.ConfigurableWithId
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

internal class PromoDatabaseConfigurable : ConfigurableWithId, Configurable.Promo {
  override fun getId(): String = "promo.database"

  override fun getDisplayName(): String {
    return JavaUiBundle.message("promo.configurable.database")
  }

  override fun createComponent(): JComponent {
    return JPanel()
  }

  override fun isModified(): Boolean = false

  override fun apply() {
  }

  override fun getPromoIcon(): Icon = AllIcons.Promo.Lock
}

internal class PromoDatabaseConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = PromoDatabaseConfigurable()
}