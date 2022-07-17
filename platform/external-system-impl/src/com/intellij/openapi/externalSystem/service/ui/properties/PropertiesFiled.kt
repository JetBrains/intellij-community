// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.properties

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesTable.Property
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.execution.ParametersListUtil
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class PropertiesFiled(project: Project, info: PropertiesInfo) : ExtendableTextField() {

  private val commandLinePropertiesProperty = AtomicProperty("")
  private val propertiesProperty = commandLinePropertiesProperty
    .transform(map = ::parseProperties, comap = ::joinProperties)

  var commandLineProperties by commandLinePropertiesProperty
  var properties by propertiesProperty

  private fun parseProperties(propertiesString: String): List<Property> {
    return ParametersListUtil.parse(propertiesString, false, true)
      .map { parseProperty(it) }
  }

  private fun parseProperty(propertyString: String): Property {
    val name = propertyString.substringBefore('=')
    val value = propertyString.substringAfter('=')
    return Property(name, value)
  }

  private fun joinProperties(properties: List<Property>): String {
    return properties.joinToString(" ") { joinProperty(it) }
  }

  private fun joinProperty(property: Property): String {
    val value = ParametersListUtil.escape(property.value)
    return property.name + "=" + value
  }

  init {
    bind(commandLinePropertiesProperty.trim())
  }

  init {
    val action = Runnable {
      val dialog = PropertiesDialog(project, info)
      dialog.properties = properties
      dialog.whenOkButtonPressed {
        properties = dialog.properties
      }
      dialog.show()
    }
    val anAction = DumbAwareAction.create { action.run() }
    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
    anAction.registerCustomShortcutSet(CustomShortcutSet(keyStroke), this, null)
    val keystrokeText = KeymapUtil.getKeystrokeText(keyStroke)
    val tooltip = info.dialogTooltip + " ($keystrokeText)"
    val browseExtension = ExtendableTextComponent.Extension.create(
      AllIcons.General.InlineVariables, AllIcons.General.InlineVariablesHover, tooltip, action)
    addExtension(browseExtension)
  }
}