// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.UnnamedConfigurable
import org.jetbrains.annotations.ApiStatus


/**
 * Provides the built-in server options in *Settings | Tools | Web Browsers and Preview | Live Preview and Built-in Server* settings.
 *
 * Register an implementation of [UnnamedConfigurable] in `plugin.xml`:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *     <builtInServerConfigurableProvider instance="class-name"/>
 * </extensions>
 * ```
 */
@ApiStatus.Internal
object BuiltInServerConfigurableEP : ConfigurableEP<UnnamedConfigurable>() {
  val EP_NAME: ExtensionPointName<BuiltInServerConfigurableEP> = ExtensionPointName("com.intellij.builtInServerConfigurableProvider")
}
