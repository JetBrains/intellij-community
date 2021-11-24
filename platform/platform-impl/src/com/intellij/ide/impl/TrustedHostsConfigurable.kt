// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

class TrustedHostsConfigurable : BoundConfigurable(IdeBundle.message("configurable.trusted.hosts.display.name"), TRUSTED_PROJECTS_HELP_TOPIC),
                                 SearchableConfigurable {

  private val EP_NAME = ExtensionPointName.create<TrustedHostsConfigurablePanelProvider>("com.intellij.trustedHostsConfigurablePanelProvider")

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        label(IdeBundle.message("trusted.folders.settings.label"))
      }
      row {
        val trustedPathsSettings = service<TrustedPathsSettings>()
        trustedLocationConfigurable(this,
                                    getValuesFromSettings = { trustedPathsSettings.getTrustedPaths() },
                                    setValuesToSettings = { trustedPathsSettings.setTrustedPaths(it) },
                                    getNewValueFromUser = { getPathFromUser(it) })
      }

      for (additionalPanel in EP_NAME.extensionList) {
        row {
          additionalPanel.getCellBuilder(this)
        }
      }
    }
  }

  private fun trustedLocationConfigurable(row: Row,
                                          getValuesFromSettings: () -> List<String>,
                                          setValuesToSettings: (List<String>) -> Unit,
                                          getNewValueFromUser: (Component) -> String?): CellBuilder<JPanel> {
    return with(row) {
      val model = CollectionListModel(getValuesFromSettings())
      val list = JBList(model)

      val component = ToolbarDecorator.createDecorator(list)
        .setAddAction {
          val path = getNewValueFromUser(list)
          if (path != null) {
            val insertPosition = if (list.selectedIndex >= 0) list.selectedIndex else max(list.itemsCount - 1, 0)
            model.add(insertPosition, path)
          }
        }
        .setRemoveAction {
          model.remove(list.selectedIndex)
        }
        .createPanel()

      component(CCFlags.growX).onApply {
        setValuesToSettings(model.items)
      }.onIsModified {
        getValuesFromSettings() != model.items
      }.onReset {
        model.replaceAll(getValuesFromSettings())
      }
    }
  }

  private fun getPathFromUser(parent: Component): String? {
    val pathField = TextFieldWithBrowseButton(null, disposable)
    pathField.textField.columns = Messages.InputDialog.INPUT_DIALOG_COLUMNS
    pathField.addBrowseFolderListener(IdeBundle.message("trusted.hosts.settings.new.trusted.folder.file.chooser.title"), null, null,
                                      FileChooserDescriptorFactory.createSingleFolderDescriptor())
    val ok = DialogBuilder(parent)
      .title(IdeBundle.message("trusted.hosts.settings.new.trusted.folder.dialog.title"))
      .setNorthPanel(pathField)
      .showAndGet()
    return if (ok) FileUtil.expandUserHome(pathField.text) else null
  }

  override fun getId(): String {
    return "trusted.hosts"
  }
}

/**
 * Provides additional components to the "Trusted Hosts" configurable in the application settings.
 */
@ApiStatus.Internal
interface TrustedHostsConfigurablePanelProvider {
  fun getCellBuilder(row: Row) : CellBuilder<JComponent>
}
