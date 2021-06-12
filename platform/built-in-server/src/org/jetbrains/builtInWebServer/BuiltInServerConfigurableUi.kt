// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.PortField
import com.intellij.ui.layout.*
import com.intellij.xml.XmlBundle
import org.jetbrains.ide.BuiltInServerBundle
import javax.swing.JCheckBox
import javax.swing.JComponent

class BuiltInServerConfigurableUi : ConfigurableUi<BuiltInServerOptions> {
  private lateinit var builtInServerPort: PortField
  private lateinit var builtInServerAvailableExternallyCheckBox: JCheckBox
  private lateinit var allowUnsignedRequestsCheckBox: JCheckBox
  private lateinit var reloadOnSaveCheckBox: JCheckBox

  private val mainPanel: DialogPanel = panel {
    row(XmlBundle.message("setting.value.builtin.server.port.label")) {
      cell {
        component(PortField().also {
          builtInServerPort = it
          it.min = 1024
          it.addChangeListener {
            val isEnabled = builtInServerPort.number < BuiltInServerOptions.DEFAULT_PORT
            builtInServerAvailableExternallyCheckBox.isEnabled = isEnabled
            builtInServerAvailableExternallyCheckBox.toolTipText =
              if (isEnabled) null
              else BuiltInServerBundle.message("checkbox.tooltip.can.t.be.enabled.for.default.port")
          }
        })
        checkBox(XmlBundle.message("setting.value.can.accept.external.connections")).withLargeLeftGap().also {
          builtInServerAvailableExternallyCheckBox = it.component
        }
      }
    }
    row {
      checkBox(XmlBundle.message("setting.value.builtin.server.allow.unsigned.requests")).also {
        allowUnsignedRequestsCheckBox = it.component
      }
    }
    row {
      checkBox(XmlBundle.message("setting.value.reload.page.on.save")).also {
        reloadOnSaveCheckBox = it.component
      }
    }
  }

  override fun reset(settings: BuiltInServerOptions) {
    builtInServerPort.number = settings.builtInServerPort
    builtInServerAvailableExternallyCheckBox.isSelected = settings.builtInServerAvailableExternally
    allowUnsignedRequestsCheckBox.isSelected = settings.allowUnsignedRequests
    reloadOnSaveCheckBox.isSelected = settings.reloadPageOnSave
  }

  override fun isModified(settings: BuiltInServerOptions): Boolean {
    return builtInServerPort.number != settings.builtInServerPort ||
           builtInServerAvailableExternallyCheckBox.isSelected != settings.builtInServerAvailableExternally ||
           allowUnsignedRequestsCheckBox.isSelected != settings.allowUnsignedRequests ||
           reloadOnSaveCheckBox.isSelected != settings.reloadPageOnSave
  }

  override fun apply(settings: BuiltInServerOptions) {
    val builtInServerPortChanged = settings.builtInServerPort != builtInServerPort.number ||
                                   settings.builtInServerAvailableExternally != builtInServerAvailableExternallyCheckBox.isSelected
    settings.allowUnsignedRequests = allowUnsignedRequestsCheckBox.isSelected
    if (builtInServerPortChanged) {
      settings.builtInServerPort = builtInServerPort.number
      settings.builtInServerAvailableExternally = builtInServerAvailableExternallyCheckBox.isSelected
      BuiltInServerOptions.onBuiltInServerPortChanged()
    }
    settings.reloadPageOnSave = reloadOnSaveCheckBox.isSelected
  }

  override fun getComponent(): JComponent = mainPanel
}