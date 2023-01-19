// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.WindowStateService
import com.intellij.troubleshooting.GeneralTroubleInfoCollector
import org.jdom.Element

class WindowStateProjectServiceTroubleInfoCollector : GeneralTroubleInfoCollector {

  override fun getTitle() = "Window State Service (per project)"

  override fun collectInfo(project: Project): String =
    WindowStateService.getInstance(project).serviceToTroubleshootingString()

}

class WindowStateApplicationServiceTroubleInfoCollector : GeneralTroubleInfoCollector {

  override fun getTitle() = "Window State Service (per app)"

  override fun collectInfo(project: Project): String =
    WindowStateService.getInstance().serviceToTroubleshootingString()

}

class DimensionServiceTroubleInfoCollector : GeneralTroubleInfoCollector {

  override fun getTitle() = "Dimension Service"

  override fun collectInfo(project: Project): String =
    DimensionService.getInstance().serviceToTroubleshootingString()

}

fun Any?.serviceToTroubleshootingString(): String = when (this) {
  null -> "Service not found"
  is PersistentStateComponent<*> -> state.stateToTroubleshootingString()
  else -> "Service ${this.javaClass.name} is not a PersistentStateComponent"
}

fun Any?.stateToTroubleshootingString(): String = when (this) {
  null -> "Service has no recorded state"
  is Element -> toTroubleshootingString()
  else -> "Service state is not an XML element: ${this.javaClass.name}"
}

fun Element.toTroubleshootingString(): String {
  val buffer = StringBuilder()
  toTroubleshootingString(buffer, this, 0)
  return buffer.toString()
}

fun toTroubleshootingString(buffer: StringBuilder, element: Element, indent: Int) {
  buffer.append(" ".repeat(indent))
  buffer.append(element.name)
  for (attribute in element.attributes) {
    buffer.append(" ").append(attribute.name).append("=").append(attribute.value)
  }
  buffer.append("\n")
  for (child in element.children) {
    toTroubleshootingString(buffer, child, indent + 4)
  }
}
