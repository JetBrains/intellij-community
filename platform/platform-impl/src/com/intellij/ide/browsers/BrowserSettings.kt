// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.IdeBundle
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel

class BrowserSettings : BoundCompositeConfigurable<UnnamedConfigurable>(
  IdeBundle.message("browsers.settings"),
  "reference.settings.ide.settings.web.browsers"
), SearchableConfigurable, WithEpDependencies {
  private var browserSettings = BrowserSettingsConfigurable()

  override fun getId(): String = helpTopic!!

  override fun createPanel(): DialogPanel {
    return panel {
      configurables.forEach {
        appendDslConfigurable(it)
      }
    }
  }

  /** Selects [browser] in the "Installed Browsers" table, see [BrowserSettingsConfigurable.selectBrowser]. */
  fun selectBrowser(browser: WebBrowser) {
    browserSettings.selectBrowser(browser)
  }

  override fun createConfigurables(): List<UnnamedConfigurable> = listOf(browserSettings)

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> = listOf(WebBrowserSettingsSectionEP.EP_NAME)
}
