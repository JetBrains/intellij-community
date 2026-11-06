// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.UnnamedConfigurable
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a section of the *Settings | Tools | Web Browsers and Preview* page.
 * The page shows the sections after the browser dropdowns, see [BrowserSettingsConfigurable].
 *
 * Register an implementation of [UnnamedConfigurable] in `plugin.xml`, or a provider that can hide the section:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *     <webBrowserSettingsSection instance="class-name"/>
 *     <webBrowserSettingsSection provider="provider-class-name"/>
 * </extensions>
 * ```
 */
@ApiStatus.Internal
object WebBrowserSettingsSectionEP : ConfigurableEP<UnnamedConfigurable>() {
  val EP_NAME: ExtensionPointName<WebBrowserSettingsSectionEP> = ExtensionPointName("com.intellij.webBrowserSettingsSection")
}
