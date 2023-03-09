// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.InspectionToolWrapper

class InspectionPathGroupProvider : InspectionGroupProvider {

  override fun findGroup(groupId: String): YamlInspectionGroup = object : YamlInspectionGroup {

    override val groupId: String = groupId

    override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
      return try {
        tool.groupPath.firstOrNull() == groupId
      }
      catch (e: AssertionError) {
        false
      }
    }
  }
}