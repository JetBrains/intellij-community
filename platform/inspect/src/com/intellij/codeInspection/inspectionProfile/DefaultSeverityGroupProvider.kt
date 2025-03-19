// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ex.InspectionToolWrapper

private const val PROVIDER_PREFIX = "severity:"

internal class DefaultSeverityGroupProvider : InspectionGroupProvider {

  override fun findGroup(groupId: String): YamlInspectionGroup? {
    if (!groupId.startsWith(PROVIDER_PREFIX)) return null
    val severityLevel = groupId.substring(PROVIDER_PREFIX.length)
    if (severityLevel.isBlank()) return null
    val displayLevel = HighlightDisplayLevel.find(severityLevel) ?: return null
    return object : YamlInspectionGroup {
      override val groupId: String = groupId

      override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
        return tool.extension?.defaultLevel == displayLevel
      }
    }
  }
}