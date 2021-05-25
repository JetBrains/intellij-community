// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages.showInputDialog
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import java.awt.Component
import javax.swing.JPanel
import kotlin.math.max

class TrustedHostsConfigurable : BoundConfigurable(IdeBundle.message("configurable.trusted.hosts.display.name")),
                                 SearchableConfigurable {
  override fun createPanel(): DialogPanel {
    return panel {
      row {
        label(IdeBundle.message("trusted.hosts.settings.label"))
      }
      row {
        val trustedHostsSettings = service<TrustedHostsSettings>()
        trustedLocationConfigurable(this,
                                    getValuesFromSettings = { trustedHostsSettings.getTrustedHosts() },
                                    setValuesToSettings = { trustedHostsSettings.setTrustedHosts(it) },
                                    getNewValueFromUser = {
                                      showInputDialog(it, null, IdeBundle.message("trusted.hosts.settings.add.new.host.dialog.title"), null)
                                    })
      }

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
    pathField.addBrowseFolderListener(IdeBundle.message("trusted.hosts.settings.new.trusted.folder.file.chooser.title"), null, null,
                                      FileChooserDescriptorFactory.createSingleFolderDescriptor())
    val ok = DialogBuilder(parent)
      .title(IdeBundle.message("trusted.hosts.settings.new.trusted.folder.dialog.title"))
      .centerPanel(pathField)
      .showAndGet()
    return if (ok) pathField.text else null
  }

  override fun getId(): String {
    return "trusted.sources"
  }
}