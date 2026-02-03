// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.UnnamedConfigurable
import org.jetbrains.annotations.ApiStatus


/**
 * Provides additional options in *Settings | Tools | Web Browsers and Preview* settings.
 *
 * Register an implementation of [UnnamedConfigurable] in `plugin.xml`:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij.ide">
 *     <browsersConfigurableProvider instance="class-name"/>
 * </extensions>
 * ```
 */
@ApiStatus.Internal
object BrowserSettingsConfigurableEP : ConfigurableEP<UnnamedConfigurable>() {
  val EP_NAME: ExtensionPointName<BrowserSettingsConfigurableEP> = ExtensionPointName<BrowserSettingsConfigurableEP>("com.intellij.browsersConfigurableProvider")
}