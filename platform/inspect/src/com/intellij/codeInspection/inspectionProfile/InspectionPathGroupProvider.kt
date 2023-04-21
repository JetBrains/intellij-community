// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.InspectionToolWrapper

private const val PATH_PROVIDER_PREFIX = "ijpath:"
class InspectionPathGroupProvider : InspectionGroupProvider {

  override fun findGroup(groupId: String): YamlInspectionGroup? {
    if (!groupId.startsWith(PATH_PROVIDER_PREFIX)) return null
    val path = groupId.substring(PATH_PROVIDER_PREFIX.length)
    if (path.isBlank()) return null
    return object : YamlInspectionGroup {

      override val groupId: String = groupId

      override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
        return try {
          tool.groupPath.joinToString("/").startsWith(path)
        }
        catch (e: AssertionError) {
          false
        }
      }
    }
  }
}