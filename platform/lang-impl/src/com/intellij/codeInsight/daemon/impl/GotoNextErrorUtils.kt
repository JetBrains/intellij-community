// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.impl.inspector.InspectionsGroup.Companion.INSPECTION_TYPED_ERROR
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun getTrafficHighlightSeverity(dataContext: DataContext?): HighlightSeverity? {
  val statusItem = dataContext?.getData(INSPECTION_TYPED_ERROR)
  if (statusItem == null) return null

  val metadata = statusItem.metadata
  if (metadata is TrafficLightStatusItemMetadata) {
    return metadata.severity
  }
  return null
}