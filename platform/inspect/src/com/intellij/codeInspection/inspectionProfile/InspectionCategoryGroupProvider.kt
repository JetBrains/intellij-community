// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.InspectionToolWrapper

private const val CATEGORY_PROVIDER_PREFIX = "category:"
class InspectionCategoryGroupProvider : InspectionGroupProvider {

  override fun findGroup(groupId: String): YamlInspectionGroup? {
    if (!groupId.startsWith(CATEGORY_PROVIDER_PREFIX)) return null
    val category = groupId.substring(CATEGORY_PROVIDER_PREFIX.length)
    if (category.isBlank()) return null
    return object : YamlInspectionGroup {

      override val groupId: String = groupId

      override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
        return try {
          tool.groupPath.joinToString("/").startsWith(category)
        }
        catch (e: AssertionError) {
          false
        }
      }
    }
  }
}