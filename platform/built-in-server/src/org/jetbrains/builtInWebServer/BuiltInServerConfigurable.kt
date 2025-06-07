// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.options.UnnamedConfigurable
import org.jetbrains.ide.BuiltInServerBundle

internal class BuiltInServerConfigurable : UnnamedConfigurable, ConfigurableBase<BuiltInServerConfigurableUi, BuiltInServerOptions>(
  "builtInServer",
  BuiltInServerBundle.message("setting.builtin.server.category.label"),
  "builtin.web.server"
) {
  override fun getSettings(): BuiltInServerOptions = BuiltInServerOptions.getInstance()

  override fun createUi(): BuiltInServerConfigurableUi = BuiltInServerConfigurableUi(displayName)
}