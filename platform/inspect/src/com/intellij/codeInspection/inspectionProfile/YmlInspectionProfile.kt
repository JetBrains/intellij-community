// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolsSupplier
import com.intellij.profile.codeInspection.BaseInspectionProfileManager

/**
 * Represents inspection profiles defined inside YAML configuration files.
 * YAML files can be used as an alternative profile configuration for headless inspection analysis.
 *
 * Includes inspection [groups] and [configurations] to define inspection settings.
 *
 * @see YamlInspectionGroup
 * @see com.intellij.codeInspection.InspectionMain
 * @see com.intellij.codeInspection.InspectionProfile
 */
interface YamlInspectionProfile : InspectionGroupProvider {
  val profileName: String?
  val inspectionToolsSupplier: InspectionToolsSupplier
  val inspectionProfileManager: BaseInspectionProfileManager
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
  val options: Map<String, String>
}

interface YamlGroupConfig : YamlBaseConfig {
  val group: String
}

/**
 * Represents a set of inspections inside the YAML inspection profile
 */
interface YamlInspectionGroup {
  val groupId: String
  fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean
}

fun YamlInspectionGroup?.includesInspection(tool: InspectionToolWrapper<*, *>): Boolean = this?.includesInspection(tool) ?: false