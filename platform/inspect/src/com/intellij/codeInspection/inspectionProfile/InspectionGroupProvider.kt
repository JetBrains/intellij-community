// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.extensions.ExtensionPointName

fun interface InspectionGroupProvider {
  companion object {
    @JvmStatic
    val EP = ExtensionPointName.create<InspectionGroupProvider>("com.intellij.inspectionGroupProvider")

    private fun createYamlInspectionGroup(groupId: String): YamlInspectionGroup {
      return object : YamlInspectionGroup {
        override val groupId: String = groupId
        override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
          return EP.extensionList.any { extension -> extension.findGroup(groupId).includesInspection(tool) }
        }
      }
    }

    @JvmStatic
    fun createDynamicGroupProvider(): InspectionGroupProvider {
      return InspectionGroupProvider { groupId -> createYamlInspectionGroup(groupId) }
    }
  }

  fun findGroup(groupId: String): YamlInspectionGroup?
}