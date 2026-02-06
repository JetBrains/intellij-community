// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.options

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.asSafely
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

class InlineCompletionConfigurable : BoundCompositeConfigurable<UnnamedConfigurable>(
  ApplicationBundle.message("title.code.completion.inline"),
  "reference.settingsdialog.IDE.editor.completion.inline"
), WithEpDependencies, SearchableConfigurable {

  override fun getId(): String = ID

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
    return listOf(InlineCompletionConfigurableEP.EP_NAME)
  }

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return ConfigurableWrapper.createConfigurables(InlineCompletionConfigurableEP.EP_NAME)
  }

  override fun createPanel(): DialogPanel {
    return panel {
      if (configurables.isEmpty()) {
        noCompletionPlugins()
      }
      configurables.forEachIndexed { index, configurable ->
        appendDslConfigurable(configurable)
        if (index != configurables.lastIndex) {
          separator()
        }
      }
    }
  }

  private fun Panel.noCompletionPlugins() {
    row {
      icon(AllIcons.General.BalloonInformation).align(AlignY.TOP)

      data class PluginInfo(val name: @Nls String, val action: HyperlinkEventAction, val suffix: @Nls String = "")

      val plugins = buildList {
        val aiaPluginInfo = PluginInfo(
          name = ApplicationBundle.message("ml.completion.advise.aia.name"),
          action = HyperlinkEventAction { e -> openPluginInSearch(e, includingMarketplace = true) }
        )
        add(aiaPluginInfo)
        val fullLinePlugin = PluginManager.getPlugins().firstOrNull {
          it.isBundled && it.name.lowercase().contains("full line")
        }
        if (fullLinePlugin != null) {
          val flPluginInfo = PluginInfo(
            name = ApplicationBundle.message("ml.completion.advise.full.line.name"),
            // Full Line is globally disabled due to RemDev if 'isEnabled = true'
            suffix = if (fullLinePlugin.isEnabled) ApplicationBundle.message("ml.completion.advise.full.line.unavailable.suffix") else "",
            action = HyperlinkEventAction { e -> openPluginInSearch(e, includingMarketplace = false) }
          )
          add(flPluginInfo)
        }
      }
      val content = plugins.joinToString(" ") {
        val suffix = if (it.suffix.isNotEmpty()) " " + it.suffix else ""
        "<li><a href=\"${it.name}\">${it.name}</a>$suffix</li>"
      }
      text(ApplicationBundle.message("ml.completion.no.plugins.text", content)) { e ->
        val pluginAction = plugins.firstOrNull { it.name == e.description } ?: return@text
        when (e.eventType) {
          HyperlinkEvent.EventType.ACTIVATED -> pluginAction.action.hyperlinkActivated(e)
          else -> Unit
        }
      }
    }
  }

  private fun openPluginInSearch(event: HyperlinkEvent, includingMarketplace: Boolean) {
    val filter = event.description
    val data = event.inputEvent.source.asSafely<JComponent>()?.let { component ->
      DataManager.getInstance().getDataContext(component)
    } ?: return
    val settings = Settings.KEY.getData(data) ?: return
    val configurable = settings.find(PluginManagerConfigurable::class.java) ?: return
    settings.select(configurable)
    configurable.enableSearch(filter, !includingMarketplace)?.run()
  }

  companion object {
    const val ID: String = "editor.preferences.completion.inline"
  }
}