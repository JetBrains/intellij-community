// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.DynamicBundle
import com.intellij.help.impl.HelpManagerImpl
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.ide.RegionSettings.RegionSettingsListener
import com.intellij.ide.plugins.DynamicPlugins
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.ui.localization.statistics.EventSource
import com.intellij.ide.ui.localization.statistics.LocalizationActionsStatistics
import com.intellij.l10n.LocalizationListener
import com.intellij.l10n.LocalizationStateService
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.whenItemSelectedFromUi
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.application
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.RestartDialog
import org.jetbrains.annotations.ApiStatus.Internal
import java.net.URL
import java.util.*
import javax.swing.JComponent
import javax.swing.ListCellRenderer
import javax.swing.event.HyperlinkEvent
import javax.swing.event.PopupMenuEvent

/**
 * @author Alexander Lobas
 */

@Internal
object LanguageAndRegionUi {
  fun createContent(panel: Panel, propertyGraph: PropertyGraph?, parentDisposable: Disposable, connection: MessageBusConnection?, source: EventSource) {
    val comboGroup = "language_and_region_combo"

    panel.row(IdeBundle.message("combobox.language")) {
      val locales = getAllAvailableLocales()
      val initSelectionLocale = LocalizationUtil.getLocale(true)
      val localizationService = LocalizationStateService.getInstance()!!
      val model = CollectionComboBoxModel(locales.first, initSelectionLocale)
      val languageBox = comboBox(model).accessibleName(IdeBundle.message("combobox.language")).widthGroup(comboGroup)
      comment(IdeBundle.message("ide.restart.required.comment"))

      if (propertyGraph != null && connection != null) {
        val property = propertyGraph.lazyProperty { LocalizationUtil.getLocale(true) }

        property.afterChange(parentDisposable) {
          val prevLocale = LocalizationUtil.getLocale(true)
          if (it.toLanguageTag() == prevLocale.toLanguageTag() || it === ITEM_MORE_LANGUAGES) {
            return@afterChange
          }

          localizationService.setSelectedLocale(it.toLanguageTag())

          showRestartDialog()
        }
        languageBox.bindItem(property)

        connection.subscribe(LocalizationListener.UPDATE_TOPIC, object : LocalizationListener {
          override fun localeChanged() {
            model.selectedItem = LocalizationUtil.getLocale(true)
          }
        })
      }
      else {
        languageBox.bindItem({ LocalizationUtil.getLocale(true) }, {
          localizationService.setSelectedLocale((it ?: Locale.ENGLISH).toLanguageTag())
        })
      }

      val languageComponent = languageBox.component
      languageComponent.isSwingPopup = false
      languageComponent.renderer = createLanguageRenderer(locales)

      var lastSelectedItem = languageComponent.selectedItem as Locale
      languageComponent.whenItemSelectedFromUi {
        if (it === ITEM_MORE_LANGUAGES) {
          model.selectedItem = lastSelectedItem
          LocalizationActionsStatistics.moreLanguagesSelected(source)
          showMoreLanguages(languageComponent)
          return@whenItemSelectedFromUi
        }
        if (lastSelectedItem == it) return@whenItemSelectedFromUi
        LocalizationActionsStatistics.languageSelected(it, lastSelectedItem, source)
        lastSelectedItem = it
      }
      languageComponent.addPopupMenuListener(object : PopupMenuListenerAdapter() {
        override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
          LocalizationActionsStatistics.languageExpanded(source)
        }
      })

      DynamicBundle.LanguageBundleEP.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<DynamicBundle.LanguageBundleEP> {
        override fun extensionAdded(extension: DynamicBundle.LanguageBundleEP, pluginDescriptor: PluginDescriptor) {
          updateComboModel()
        }

        override fun extensionRemoved(extension: DynamicBundle.LanguageBundleEP, pluginDescriptor: PluginDescriptor) {
          updateComboModel()
        }

        private fun updateComboModel() {
          val newLocales = getAllAvailableLocales()
          var selection = languageComponent.selectedItem as Locale
          if (!newLocales.first.contains(selection)) {
            selection = newLocales.first.first()
          }
          languageComponent.renderer = createLanguageRenderer(newLocales)
          languageComponent.model = CollectionComboBoxModel(newLocales.first, selection)
        }
      }, parentDisposable)
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

          showRestartDialog()
        }
        regionBox.bindItem(property)

        connection.subscribe(RegionSettingsListener.UPDATE_TOPIC, RegionSettingsListener {
          model.selectedItem = RegionSettings.getRegion()
        })

        regionBox.gap(RightGap.SMALL)
        cell(ContextHelpLabel.createWithBrowserLink(null, IdeBundle.message("combobox.region.hint"),
                                                    IdeBundle.message("combobox.region.hint.link"), URL(helpUrl)))
      }
      else {
        regionBox.bindItem({ RegionSettings.getRegion() }, { RegionSettings.setRegion(it ?: Region.NOT_SET) })

        regionBox.comment(IdeBundle.message("combobox.region.comment", helpUrl))

        regionBox.comment?.addHyperlinkListener { e ->
          if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            LocalizationActionsStatistics.hyperLinkActivated(source)
          }
        }
      }

      val regionComponent = regionBox.component
      regionComponent.isSwingPopup = false
      regionComponent.renderer = listCellRenderer {
        if (value == Region.NOT_SET) {
          separator { }
        }
        text(value.displayName)
      }

      var lastSelectedItem = regionComponent.selectedItem as Region
      regionComponent.whenItemSelectedFromUi {
        if (lastSelectedItem == it) return@whenItemSelectedFromUi
        LocalizationActionsStatistics.regionSelected(it, lastSelectedItem, source)
        lastSelectedItem = it
      }
      regionComponent.addPopupMenuListener(object : PopupMenuListenerAdapter() {
        override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
          LocalizationActionsStatistics.regionExpanded(source)
        }
      })
    }
  }

  fun showRestartDialog(runAlways: Boolean = true) {
    DynamicPlugins.runAfter(runAlways) {
      application.invokeLater {
        application.service<RestartDialog>().showRestartRequired()
      }
    }
  }

  private fun getAllAvailableLocales(): Pair<List<Locale>, Map<Locale, String>> {
    val availableLocales = LocalizationUtil.getAllAvailableLocales()
    return buildList {
      addAll(availableLocales.first)
      add(ITEM_MORE_LANGUAGES)
    } to availableLocales.second
  }

  private fun showMoreLanguages(comboBoxComponent: JComponent) {
    val tag = "/tag:\"Language Pack\""
    val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(comboBoxComponent))

    if (settings == null) {
      ShowSettingsUtil.getInstance().showSettingsDialog(ProjectManager.getInstance().defaultProject, PluginManagerConfigurable::class.java) {
        it.enableSearch(tag)
      }
    }
    else {
      settings.select(settings.find("preferences.pluginManager"), tag)
    }
  }

  internal val ITEM_MORE_LANGUAGES = Locale("more languages")
}

internal class LanguageAndRegionConfigurable :
  BoundSearchableConfigurable(IdeBundle.message("title.language.and.region"), "language-region-settings", "preferences.language.and.region") {
  private lateinit var initSelectionLanguage: Locale
  private lateinit var initSelectionRegion: Region
  private val eventSource: EventSource = EventSource.SETTINGS
  override fun createPanel(): DialogPanel {
    initSelectionLanguage = LocalizationUtil.getLocale(true)
    initSelectionRegion = RegionSettings.getRegion()

    return panel {
      useNewComboBoxRenderer()

      LanguageAndRegionUi.createContent(this, null, disposable!!, null, eventSource)
    }
  }

  override fun apply() {
    super.apply()
    val selectedLocale = LocalizationUtil.getLocale(true)
    val selectedRegion = RegionSettings.getRegion()
    if (initSelectionLanguage.toLanguageTag() != selectedLocale.toLanguageTag() ||
        initSelectionRegion != selectedRegion) {
      LocalizationActionsStatistics.settingsUpdated(selectedLocale, initSelectionLanguage, selectedRegion, initSelectionRegion, eventSource)
      LanguageAndRegionUi.showRestartDialog()
    }
  }
}

private fun createLanguageRenderer(locales: Pair<List<Locale>, Map<Locale, String>>): ListCellRenderer<Locale> {
  return listCellRenderer {
    if (value === LanguageAndRegionUi.ITEM_MORE_LANGUAGES) {
      separator { }
      text(IdeBundle.message("item.get.more.languages"))
    }
    else {
      if (locales.first.indexOf(value) == 1) {
        separator { }
      }
      text(locales.second[value] ?: value.getDisplayLanguage(Locale.ENGLISH))
    }
  }
}
