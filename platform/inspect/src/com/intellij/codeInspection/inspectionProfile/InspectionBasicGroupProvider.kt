// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.InspectionToolWrapper

private object AllGroup : YamlInspectionGroup {
  override val groupId: String = "ALL"
  override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean = true
}

private object GlobalGroup : YamlInspectionGroup {
  override val groupId: String = "GLOBAL"
  override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
    return tool.tool is GlobalInspectionTool
  }
}

private object LocalGroup : YamlInspectionGroup {
  override val groupId: String = "LOCAL"
  override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
    return tool.tool is LocalInspectionTool
  }
}

class InspectionBasicGroupProvider : InspectionGroupProvider {
  private val commonGroups: Map<String, YamlInspectionGroup> by lazy {
    listOf(LocalGroup, GlobalGroup, AllGroup).associateBy(YamlInspectionGroup::groupId)
  }

  override fun findGroup(groupId: String): YamlInspectionGroup? {
    return commonGroups[groupId]
  }
}

