// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.help.impl.HelpManagerImpl
import com.intellij.ide.IdeBundle
import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.l10n.LocalizationStateService
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.RestartDialog
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

/**
 * @author Alexander Lobas
 */

@Internal
class LanguageAndRegionUi {
  companion object {
    fun createContent(panel: Panel, propertyGraph: PropertyGraph?, parentDisposable: Disposable?, connection: MessageBusConnection?) {
      val comboGroup = "language_and_region_combo"

      panel.row(IdeBundle.message("combobox.language")) {
        val items = LocalizationUtil.getAllAvailableLocales()
        val localizationService = LocalizationStateService.getInstance()!!
        val selection = Locale.forLanguageTag(localizationService.getSelectedLocale())
        val model = CollectionComboBoxModel(items, selection)
        val languageBox = comboBox(model).accessibleName(IdeBundle.message("combobox.language")).widthGroup(comboGroup)

        if (propertyGraph != null && parentDisposable != null && connection != null) {
          val property = propertyGraph.lazyProperty { Locale.forLanguageTag(localizationService.getSelectedLocale()) }

          property.afterChange(parentDisposable) {
            if (it.toLanguageTag() != localizationService.getSelectedLocale()) {
              localizationService.setSelectedLocale(it.toLanguageTag())
            }
          }
          languageBox.bindItem(property)

          connection.subscribe(LocalizationStateService.UPDATE_TOPIC, Runnable {
            model.selectedItem = Locale.forLanguageTag(localizationService.getSelectedLocale())
          })
        }
        else {
          languageBox.bindItem({ Locale.forLanguageTag(localizationService.getSelectedLocale()) }, {
            localizationService.setSelectedLocale((it ?: Locale.ENGLISH).toLanguageTag())
          })
        }

        val languageComponent = languageBox.component
        languageComponent.isSwingPopup = false
        languageComponent.renderer = LanguageComboBoxRenderer(items)
      }

      panel.row(IdeBundle.message("combobox.region")) {
        val helpUrl = (HelpManager.getInstance() as HelpManagerImpl).getHelpUrl("preferences.language.and.region")
        val comment = IdeBundle.message("combobox.region.comment", helpUrl)

        val model = CollectionComboBoxModel(Region.entries, RegionSettings.getRegion())
        val regionBox = comboBox(model).accessibleName(IdeBundle.message("combobox.region")).widthGroup(comboGroup)

        if (propertyGraph != null && parentDisposable != null && connection != null) {
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
          contextHelp(comment)
        }
        else {
          regionBox.bindItem({ RegionSettings.getRegion() }, { RegionSettings.setRegion(it ?: Region.NOT_SET) })

          regionBox.comment(comment)
        }

        val regionComponent = regionBox.component
        regionComponent.isSwingPopup = false
        regionComponent.renderer = RegionComboBoxRenderer()
      }
    }
  }
}

internal class LanguageAndRegionConfigurable :
  BoundSearchableConfigurable(IdeBundle.message("title.language.and.region"), "preferences.language.and.region") {
  private lateinit var initSelectionRegion: Region

  override fun createPanel(): DialogPanel {
    initSelectionRegion = RegionSettings.getRegion()

    return panel {
      LanguageAndRegionUi.createContent(this, null, null, null)
    }
  }

  override fun apply() {
    super.apply()
    if (initSelectionRegion != RegionSettings.getRegion()) {
      ApplicationManager.getApplication().invokeLater {
        RestartDialog.showRestartRequired()
      }
    }
  }
}

private class LanguageComboBoxRenderer(private val allLocales: kotlin.collections.List<Locale>) : GroupedComboBoxRenderer<Locale>() {
  override fun getText(item: Locale): String {
    return item.displayLanguage
  }

  override fun separatorFor(value: Locale): ListSeparator? {
    if (allLocales.indexOf(value) == 1) {
      return ListSeparator()
    }
    return null
  }
}

private class RegionComboBoxRenderer : GroupedComboBoxRenderer<Region>() {
  override fun getText(item: Region): String {
    return IdeBundle.message("title.region.${item.name.lowercase(Locale.getDefault())}")
  }

  override fun separatorFor(value: Region): ListSeparator? {
    if (value == Region.AFRICA) {
      return ListSeparator()
    }
    return null
  }
}