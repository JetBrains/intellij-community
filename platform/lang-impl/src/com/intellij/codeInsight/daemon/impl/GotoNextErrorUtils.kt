// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.impl.inspector.InspectionsGroup.Companion.INSPECTION_TYPED_ERROR
import com.intellij.openapi.editor.markup.InspectionsFUS
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun getTrafficHighlightSeverity(dataContext: DataContext?): HighlightSeverity? {
  return getTrafficHighlightMetadata(dataContext)?.severity
}

@ApiStatus.Internal
fun reportTrafficHighlightStatistic(e: AnActionEvent, goForward: Boolean) {
  val metadata = getTrafficHighlightMetadata(e.dataContext) ?: return
  val type = when (metadata.severity) {
    HighlightSeverity.ERROR -> InspectionsFUS.InspectionSegmentType.Error
    HighlightSeverity.WARNING -> InspectionsFUS.InspectionSegmentType.Warning
    HighlightSeverity.WEAK_WARNING -> InspectionsFUS.InspectionSegmentType.WeakWarning
    HighlightSeverity.INFORMATION -> InspectionsFUS.InspectionSegmentType.Information
    HighlightSeverity.INFO -> InspectionsFUS.InspectionSegmentType.InformationDeprecated
    HighlightSeverity.TEXT_ATTRIBUTES -> InspectionsFUS.InspectionSegmentType.Consideration
    HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING -> InspectionsFUS.InspectionSegmentType.ServerProblem
    else -> InspectionsFUS.InspectionSegmentType.Other
  }
  InspectionsFUS.segmentClick(e.project, type, metadata.count, goForward)
}

private fun getTrafficHighlightMetadata(dataContext: DataContext?): TrafficLightStatusItemMetadata? {
  val statusItem = dataContext?.getData(INSPECTION_TYPED_ERROR)
  if (statusItem == null) return null

  val metadata = statusItem.metadata
  if (metadata is TrafficLightStatusItemMetadata) {
    return metadata
  }
  return null
}