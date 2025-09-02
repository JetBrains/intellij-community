// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import com.intellij.ui.PortField
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.ide.BuiltInServerBundle
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class BuiltInServerConfigurableUi(@ConfigurableName private val displayName: String) : ConfigurableUi<BuiltInServerOptions> {
  private lateinit var builtInServerPort: PortField
  private lateinit var builtInServerAvailableExternallyCheckBox: JCheckBox
  private lateinit var allowUnsignedRequestsCheckBox: JCheckBox

  private val mainPanel: DialogPanel = panel {
    group(displayName) {
      row(BuiltInServerBundle.message("setting.value.builtin.server.port.label")) {
        builtInServerPort = cell(PortField())
          .applyToComponent {
            min = 1024
            addChangeListener {
              val isEnabled = builtInServerPort.number < BuiltInServerOptions.DEFAULT_PORT
              builtInServerAvailableExternallyCheckBox.isEnabled = isEnabled
              builtInServerAvailableExternallyCheckBox.toolTipText =
                if (isEnabled) null
                else BuiltInServerBundle.message("checkbox.tooltip.can.t.be.enabled.for.default.port")
            }
          }.component
        contextHelp(BuiltInServerBundle.message("setting.builtin.server.tip"))
      }
      row {
        builtInServerAvailableExternallyCheckBox = checkBox(BuiltInServerBundle.message("setting.value.can.accept.external.connections")).component
      }
      row {
        allowUnsignedRequestsCheckBox = checkBox(BuiltInServerBundle.message("setting.value.builtin.server.allow.unsigned.requests")).component
      }
    }
  }

  override fun reset(settings: BuiltInServerOptions) {
    builtInServerPort.number = settings.builtInServerPort
    builtInServerAvailableExternallyCheckBox.isSelected = settings.builtInServerAvailableExternally
    allowUnsignedRequestsCheckBox.isSelected = settings.allowUnsignedRequests
  }

  override fun isModified(settings: BuiltInServerOptions): Boolean {
    return builtInServerPort.number != settings.builtInServerPort ||
           builtInServerAvailableExternallyCheckBox.isSelected != settings.builtInServerAvailableExternally ||
           allowUnsignedRequestsCheckBox.isSelected != settings.allowUnsignedRequests
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
  }

  override fun getComponent(): JComponent = mainPanel
}