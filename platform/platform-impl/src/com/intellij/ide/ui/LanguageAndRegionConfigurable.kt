// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.DynamicBundle
import com.intellij.help.impl.HelpManagerImpl
import com.intellij.ide.IdeBundle
import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.l10n.LocalizationListener
import com.intellij.l10n.LocalizationStateService
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.RestartDialog
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus.Internal
import java.net.URL
import java.util.*

/**
 * @author Alexander Lobas
 */

@Internal
class LanguageAndRegionUi {
  companion object {
    fun createContent(panel: Panel, propertyGraph: PropertyGraph?, parentDisposable: Disposable, connection: MessageBusConnection?) {
      val comboGroup = "language_and_region_combo"

      panel.row(IdeBundle.message("combobox.language")) {
        val locales = LocalizationUtil.getAllAvailableLocales()
        val localizationService = LocalizationStateService.getInstance()!!
        val forcedLocale = LocalizationUtil.getForcedLocale()
        val model = CollectionComboBoxModel(locales.first, LocalizationUtil.getLocale())
        val languageBox = comboBox(model).accessibleName(IdeBundle.message("combobox.language")).widthGroup(comboGroup)

        if (forcedLocale == null) {
          languageBox.gap(RightGap.SMALL)
          comment(IdeBundle.message("ide.restart.required.comment"))
        }

        if (forcedLocale != null) {
          languageBox.enabled(false)
            .comment(IdeBundle.message("combobox.language.disable.comment", LocalizationUtil.LOCALIZATION_KEY, forcedLocale))
        }
        else if (propertyGraph != null && connection != null) {
          val property = propertyGraph.lazyProperty { LocalizationUtil.getLocale() }

          property.afterChange(parentDisposable) {
            if (it.toLanguageTag() == LocalizationUtil.getLocale().toLanguageTag()) {
              return@afterChange
            }

            localizationService.setSelectedLocale(it.toLanguageTag())

            ApplicationManager.getApplication().invokeLater {
              RestartDialog.showRestartRequired()
            }
          }
          languageBox.bindItem(property)

          connection.subscribe(LocalizationListener.UPDATE_TOPIC, Runnable {
            model.selectedItem = LocalizationUtil.getLocale()
          })
        }
        else {
          languageBox.bindItem({ LocalizationUtil.getLocale() }, {
            localizationService.setSelectedLocale((it ?: Locale.ENGLISH).toLanguageTag())
          })
        }

        val languageComponent = languageBox.component
        languageComponent.isSwingPopup = false
        languageComponent.renderer = LanguageComboBoxRenderer(locales)

        if (forcedLocale == null) {
          DynamicBundle.LanguageBundleEP.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<DynamicBundle.LanguageBundleEP> {
            override fun extensionAdded(extension: DynamicBundle.LanguageBundleEP, pluginDescriptor: PluginDescriptor) {
              updateComboModel()
            }

            override fun extensionRemoved(extension: DynamicBundle.LanguageBundleEP, pluginDescriptor: PluginDescriptor) {
              updateComboModel()
            }

            private fun updateComboModel() {
              val newLocales = LocalizationUtil.getAllAvailableLocales()
              languageComponent.renderer = LanguageComboBoxRenderer(newLocales)
              languageComponent.model = CollectionComboBoxModel(newLocales.first, languageComponent.selectedItem as Locale)
            }
          }, parentDisposable)
        }
      }

      panel.row(IdeBundle.message("combobox.region")) {
        val helpUrl = HelpManagerImpl.getHelpUrl("region-settings")

        val model = CollectionComboBoxModel(Region.entries.sortedBy { it.displayOrdinal }, RegionSettings.getRegion())
        val regionBox = comboBox(model).accessibleName(IdeBundle.message("combobox.region")).widthGroup(comboGroup)

        if (propertyGraph != null && connection != null) {
          val property = propertyGraph.lazyProperty { RegionSettings.getRegion() }

          property.afterChange(parentDisposable) {
            if (it == RegionSettings.getRegion()) {
              return@afterChange
            }

            RegionSettings.setRegion(it)

            ApplicationManager.getApplication().invokeLater {
              RestartDialog.showRestartRequired()
            }
          }
          regionBox.bindItem(property)

          connection.subscribe(RegionSettings.UPDATE_TOPIC, Runnable { model.selectedItem = RegionSettings.getRegion() })

          regionBox.gap(RightGap.SMALL)
          cell(ContextHelpLabel.createWithBrowserLink(null, IdeBundle.message("combobox.region.hint"),
                                                      IdeBundle.message("combobox.region.hint.link"), URL(helpUrl)))
        }
        else {
          regionBox.bindItem({ RegionSettings.getRegion() }, { RegionSettings.setRegion(it ?: Region.NOT_SET) })

          regionBox.comment(IdeBundle.message("combobox.region.comment", helpUrl))
        }

        val regionComponent = regionBox.component
        regionComponent.isSwingPopup = false
        regionComponent.renderer = RegionComboBoxRenderer()
      }
    }
  }
}

internal class LanguageAndRegionConfigurable :
  BoundSearchableConfigurable(IdeBundle.message("title.language.and.region"), "region-settings", "preferences.language.and.region") {
  private lateinit var initSelectionLanguage: Locale
  private lateinit var initSelectionRegion: Region

  override fun createPanel(): DialogPanel {
    initSelectionLanguage = LocalizationUtil.getLocale()
    initSelectionRegion = RegionSettings.getRegion()

    return panel {
      LanguageAndRegionUi.createContent(this, null, disposable!!, null)
    }
  }

  override fun apply() {
    super.apply()
    if (initSelectionLanguage.toLanguageTag() != LocalizationUtil.getLocale().toLanguageTag() ||
        initSelectionRegion != RegionSettings.getRegion()) {
      ApplicationManager.getApplication().invokeLater {
        RestartDialog.showRestartRequired()
      }
    }
  }
}

private class LanguageComboBoxRenderer(private val locales: Pair<kotlin.collections.List<Locale>, Map<Locale, String>>) :
  GroupedComboBoxRenderer<Locale>() {

  override fun getText(item: Locale): @NlsSafe String {
    return locales.second[item] ?: item.getDisplayLanguage(Locale.ENGLISH)
  }

  override fun separatorFor(value: Locale): ListSeparator? {
    if (locales.first.indexOf(value) == 1) {
      return ListSeparator()
    }
    return null
  }
}

private class RegionComboBoxRenderer : GroupedComboBoxRenderer<Region>() {
  override fun getText(item: Region): String {
    return item.displayName
  }

  override fun separatorFor(value: Region): ListSeparator? {
    if (value == Region.NOT_SET) {
      return ListSeparator()
    }
    return null
  }
}