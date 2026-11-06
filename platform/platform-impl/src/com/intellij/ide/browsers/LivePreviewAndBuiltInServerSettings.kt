// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.IdeBundle
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import javax.swing.JComponent

/**
 * The child page of [BrowserSettings]. It shows the reload behavior and the built-in server options of [BuiltInServerConfigurableEP].
 */
internal class LivePreviewAndBuiltInServerSettings : BoundCompositeConfigurable<UnnamedConfigurable>(
  IdeBundle.message("browsers.live.preview.settings"),
  ID
), SearchableConfigurable, WithEpDependencies {
  override fun getId(): String = ID

  override fun createPanel(): DialogPanel {
    return panel {
      configurables.forEach {
        appendDslConfigurable(it)
      }
    }
  }

  override fun createConfigurables(): List<UnnamedConfigurable> = buildList {
    add(ReloadBehaviorConfigurable())
    addAll(ConfigurableWrapper.createConfigurables(BuiltInServerConfigurableEP.EP_NAME))
  }

  override fun getDependencies(): Collection<BaseExtensionPointName<*>?> = listOf(BuiltInServerConfigurableEP.EP_NAME)

  companion object {
    const val ID: String = "reference.settings.ide.settings.web.browsers.live.preview"
  }
}

private class ReloadBehaviorConfigurable : UnnamedConfigurable {
  private lateinit var serverReloadModeComboBox: ComboBox<ReloadMode>
  private lateinit var previewReloadModeComboBox: ComboBox<ReloadMode>

  override fun createComponent(): JComponent = panel {
    group(IdeBundle.message("settings.browsers.reload.behavior")) {
      row(IdeBundle.message("setting.value.reload.mode.server")) {
        serverReloadModeComboBox = comboBox(ReloadMode.entries, textListCellRenderer("") { it.title })
          .component
      }
      row(IdeBundle.message("setting.value.reload.mode.preview")) {
        previewReloadModeComboBox = comboBox(ReloadMode.entries, textListCellRenderer("") { it.title })
          .component
      }
    }
  }

  override fun isModified(): Boolean {
    val browserManager = WebBrowserManager.getInstance()
    return browserManager.webPreviewReloadMode !== previewReloadModeComboBox.item ||
           browserManager.webServerReloadMode !== serverReloadModeComboBox.item
  }

  override fun apply() {
    val browserManager = WebBrowserManager.getInstance()
    browserManager.webPreviewReloadMode = previewReloadModeComboBox.item
    browserManager.webServerReloadMode = serverReloadModeComboBox.item
  }

  override fun reset() {
    val browserManager = WebBrowserManager.getInstance()
    previewReloadModeComboBox.item = browserManager.webPreviewReloadMode
    serverReloadModeComboBox.item = browserManager.webServerReloadMode
  }
}
