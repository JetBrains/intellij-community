// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.troubleshooting.GeneralTroubleInfoCollector
import org.jdom.Element

internal fun collectDimensionServiceDiagnosticsData(project: Project): String {
  return CompositeGeneralTroubleInfoCollector.collectInfo(
    project,
    ProjectFrameTroubleInfoCollector(),
    AppFrameTroubleInfoCollector(),
    WindowStateProjectServiceTroubleInfoCollector(),
    WindowStateApplicationServiceTroubleInfoCollector(),
    DimensionServiceTroubleInfoCollector()
  )
}

private class ProjectFrameTroubleInfoCollector : GeneralTroubleInfoCollector {
  override fun getTitle() = "IDE Frame (per project)"

  override fun collectInfo(project: Project): String =
    RecentProjectsManagerBase.getInstanceEx().state.additionalInfo.entries
      .joinToString("\n") { entry -> "${entry.key}: ${entry.value.frame?.toString()}" }

}

private class AppFrameTroubleInfoCollector : GeneralTroubleInfoCollector {
  override fun getTitle() = "IDE Frame (per app)"

  override fun collectInfo(project: Project): String =
    (WindowManagerEx.getInstanceEx() as? WindowManagerImpl).serviceToTroubleshootingString()

}

private class WindowStateProjectServiceTroubleInfoCollector : GeneralTroubleInfoCollector {

  override fun getTitle() = "Window State Service (per project)"

  override fun collectInfo(project: Project): String =
    WindowStateService.getInstance(project).serviceToTroubleshootingString()

}

private class WindowStateApplicationServiceTroubleInfoCollector : GeneralTroubleInfoCollector {

  override fun getTitle() = "Window State Service (per app)"

  override fun collectInfo(project: Project): String =
    WindowStateService.getInstance().serviceToTroubleshootingString()

}

private class DimensionServiceTroubleInfoCollector : GeneralTroubleInfoCollector {

  override fun getTitle() = "Dimension Service"

  override fun collectInfo(project: Project): String =
    DimensionService.getInstance().serviceToTroubleshootingString()

}

private fun Any?.serviceToTroubleshootingString(): String = when (this) {
  null -> "Service not found"
  is PersistentStateComponent<*> -> state.stateToTroubleshootingString()
  else -> "Service ${this.javaClass.name} is not a PersistentStateComponent"
}

private fun Any?.stateToTroubleshootingString(): String = when (this) {
  null -> "Service has no recorded state"
  is Element -> toTroubleshootingString()
  else -> "Service state is not an XML element: ${this.javaClass.name}"
}

private fun Element.toTroubleshootingString(): String {
  val buffer = StringBuilder()
  toTroubleshootingString(buffer, this, 0)
  return buffer.toString()
}

private fun toTroubleshootingString(buffer: StringBuilder, element: Element, indent: Int) {
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
