// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.plugins.newui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.setToolTipText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.updateSettings.impl.PluginUpdateExclusionService.Exclusion
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import javax.swing.DefaultListSelectionModel
import javax.swing.JButton
import javax.swing.ListSelectionModel

internal class PluginUpdateModeButton(
  private val pluginIdProvider: () -> PluginId?,
  private val pluginModel: PluginModelFacade,
  private val coroutineScope: CoroutineScope,
  private val pluginsAutoUpdateEnabledProvider: () -> Boolean,
) : JButton(AllIcons.Actions.More) {
  init {
    val accessibleName = IdeBundle.message("plugins.configurable.update.mode.action")
    setToolTipText(HtmlChunk.text(accessibleName))
    getAccessibleContext().accessibleName = accessibleName
    preferredSize = Dimension(preferredSize.height, preferredSize.height)
    isVisible = true
    val component = this
    addActionListener {
      ApplicationManager.getApplication().invokeLater({
        createPopup().showUnderneathOf(component)
    }, ModalityState.current())
    }
  }

  private fun createPopup(): JBPopup {
    val selectedItem = getSelectedPopupItem()
    val defaultItem = getDefaultPopupItem()
    val modeItems = getPluginUpdateModePopupItems(pluginsAutoUpdateEnabledProvider())
    val selectedPopupItem = PluginUpdateModePopupListItem.Mode(selectedItem)
    val list = JBList(CollectionListModel(buildList {
      add(PluginUpdateModePopupListItem.Updates)
      modeItems.mapTo(this) { PluginUpdateModePopupListItem.Mode(it) }
    }))
    list.selectionModel = PluginUpdateModePopupSelectionModel()
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.setSelectedValue(selectedPopupItem, true)

    return PopupChooserBuilder(list)
      .setRenderer(listCellRenderer {
        when (val item = value) {
          null -> Unit
          PluginUpdateModePopupListItem.Updates -> {
            selectable = false
            text(IdeBundle.message("plugins.configurable.update.mode.popup.title")) {
              foreground = greyForeground
            }
          }
          is PluginUpdateModePopupListItem.Mode -> {
            icon(if (item.item == selectedItem) AllIcons.Actions.SetDefault else AllIcons.Empty)
            text(item.item.text())
            if (item.item == defaultItem) {
              text(IdeBundle.message("plugins.configurable.update.mode.default")) {
                foreground = greyForeground
              }
            }
          }
        }
      })
      .setItemChosenCallback { item ->
        if (item is PluginUpdateModePopupListItem.Mode) {
          handlePopupItem(item.item)
        }
      }
      .createPopup()
  }

  private fun getSelectedPopupItem(): PluginUpdateModePopupItem {
    val pluginId = pluginIdProvider() ?: return PluginUpdateModePopupItem.MANUAL_UPDATE
    val exclusion = pluginModel.getPluginUpdateExclusion(pluginId)
    return when (exclusion) {
      null -> getDefaultPopupItem()
      Exclusion.FromAutoUpdate -> PluginUpdateModePopupItem.MANUAL_UPDATE
      Exclusion.FromUpdate -> PluginUpdateModePopupItem.IGNORE_UPDATES
    }
  }

  private fun getDefaultPopupItem(): PluginUpdateModePopupItem {
    if (pluginsAutoUpdateEnabledProvider()) {
      return PluginUpdateModePopupItem.AUTO_UPDATE
    }
    else {
      return PluginUpdateModePopupItem.MANUAL_UPDATE
    }
  }

  private fun handlePopupItem(item: PluginUpdateModePopupItem) {
    val exclusion: Exclusion? = when (item) {
      PluginUpdateModePopupItem.AUTO_UPDATE -> null
      PluginUpdateModePopupItem.MANUAL_UPDATE -> Exclusion.FromAutoUpdate
      PluginUpdateModePopupItem.IGNORE_UPDATES -> Exclusion.FromUpdate
    }
    val pluginId = pluginIdProvider() ?: return
    val modalityState = ModalityState.current()
    coroutineScope.launch(Dispatchers.IO + modalityState.asContextElement()) {
      pluginModel.persistPluginUpdateExclusion(pluginId, exclusion)
      val isMissingUpdateSourceWarningEnabled = pluginModel.isMissingUpdateSourceWarningEnabled()
      withContext(Dispatchers.EDT) {
        pluginModel.getModel().updateUiAfterUpdateExclusionChange(
          pluginId,
          pluginUpdateExclusion = pluginModel.getPluginUpdateExclusion(pluginId),
          isMissingUpdateSourceWarningEnabled = isMissingUpdateSourceWarningEnabled,
        )
      }
    }
  }
}

private sealed interface PluginUpdateModePopupListItem {
  data object Updates : PluginUpdateModePopupListItem
  data class Mode(val item: PluginUpdateModePopupItem) : PluginUpdateModePopupListItem
}

private class PluginUpdateModePopupSelectionModel : DefaultListSelectionModel() {
  override fun setSelectionInterval(index0: Int, index1: Int) {
    if (index0 == 0 || index1 == 0) return
    super.setSelectionInterval(index0, index1)
  }

  override fun addSelectionInterval(index0: Int, index1: Int) {
    if (index0 == 0 || index1 == 0) return
    super.addSelectionInterval(index0, index1)
  }
}

internal enum class PluginUpdateModePopupItem(private val messageKey: String) {
  AUTO_UPDATE("plugins.configurable.update.mode.auto"),
  MANUAL_UPDATE("plugins.configurable.update.mode.manual"),
  IGNORE_UPDATES("plugins.configurable.update.mode.ignore");

  fun text(): @Nls String = IdeBundle.message(messageKey)
}

internal fun getPluginUpdateModePopupItems(isPluginAutoUpdateEnabled: Boolean): List<PluginUpdateModePopupItem> {
  return buildList {
    if (isPluginAutoUpdateEnabled) {
      add(PluginUpdateModePopupItem.AUTO_UPDATE)
    }
    add(PluginUpdateModePopupItem.MANUAL_UPDATE)
    add(PluginUpdateModePopupItem.IGNORE_UPDATES)
  }
}
