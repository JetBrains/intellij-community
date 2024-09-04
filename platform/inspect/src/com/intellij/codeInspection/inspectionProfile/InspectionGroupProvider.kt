// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Extension provides pre-defined inspection groups which can be used inside the YAML inspection profile.
 * @see InspectionBasicGroupProvider
 * @see YamlInspectionProfile
 */
fun interface InspectionGroupProvider {
  fun findGroup(groupId: String): YamlInspectionGroup?
}

internal object InspectionGroupProviderEP {
  private val EP: ExtensionPointName<InspectionGroupProvider> = ExtensionPointName.create("com.intellij.inspectionGroupProvider")

  private fun createYamlInspectionGroup(groupId: String): YamlInspectionGroup? {
    return EP.extensionList.firstNotNullOfOrNull { extension -> extension.findGroup(groupId) }
  }

  fun createDynamicGroupProvider(): InspectionGroupProvider {
    return InspectionGroupProvider { groupId -> createYamlInspectionGroup(groupId) }
  }
}
