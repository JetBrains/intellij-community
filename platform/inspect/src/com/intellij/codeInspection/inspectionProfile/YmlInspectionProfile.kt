// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper

interface YamlInspectionProfile : InspectionGroupProvider {
  val profileName: String?
  val baseProfile: InspectionProfileImpl
  val configurations: List<YamlBaseConfig>
  val groups: List<YamlInspectionGroup>
}

sealed interface YamlBaseConfig {
  val enabled: Boolean?
  val severity: String?
  val ignore: List<String>
}

interface YamlInspectionConfig : YamlBaseConfig {
  val inspection: String
  val options: Map<String, *>
}

interface YamlGroupConfig : YamlBaseConfig {
  val group: String
}

interface YamlInspectionGroup {
  val groupId: String
  fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean
}

fun YamlInspectionGroup?.includesInspection(tool: InspectionToolWrapper<*, *>): Boolean = this?.includesInspection(tool) ?: false