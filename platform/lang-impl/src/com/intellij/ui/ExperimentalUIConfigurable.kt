// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.options.ConfigurableBase

/**
 * @author Konstantin Bulenkov
 */
class ExperimentalUIConfigurable : ConfigurableBase<ExperimentalUIConfigurableUi, ExperimentalUI>("reference.settings.ide.settings.new.ui",
                                                                                                  IdeBundle.message("configurable.new.ui.name"),
                                                                                                  null) {
  override fun getSettings(): ExperimentalUI = ExperimentalUI.getInstance()

  override fun createUi(): ExperimentalUIConfigurableUi = ExperimentalUIConfigurableUi()
}