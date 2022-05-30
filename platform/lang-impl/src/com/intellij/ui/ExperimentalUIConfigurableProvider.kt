// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ConfigurableProvider

/**
 * @author Konstantin Bulenkov
 */
class ExperimentalUIConfigurableProvider: ConfigurableProvider() {
  override fun createConfigurable() = ExperimentalUIConfigurable()

  override fun canCreateConfigurable() = ApplicationManager.getApplication().isInternal
}