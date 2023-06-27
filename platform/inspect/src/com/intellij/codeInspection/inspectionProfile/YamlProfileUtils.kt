// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.DynamicGroupTool
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolsSupplier
import com.intellij.codeInspection.ex.ToolsImpl
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import org.jdom.Element

object YamlProfileUtils {

  private fun copyFrom(source: ToolsImpl, target: ToolsImpl) {
    val defaultState = source.defaultState
    val toolWrapper = InspectionProfileImpl.copyToolSettings(defaultState.tool)
    target.setDefaultState(toolWrapper, defaultState.isEnabled, defaultState.level, defaultState.editorAttributesExternalName)
    target.removeAllScopes()
    source.nonDefaultTools?.forEach {
      val scope = it.getScope(null)
      val tool = if (scope != null) {
        target.addTool(scope, it.tool, it.isEnabled, it.level)
      }
      else {
        target.addTool(it.scopeName, it.tool, it.isEnabled, it.level)
      }
      tool.editorAttributesExternalName = it.editorAttributesExternalName
    }
    target.isEnabled = source.isEnabled
  }

  fun createProfileCopy(baseProfile: InspectionProfileImpl,
                        inspectionToolsSupplier: InspectionToolsSupplier,
                        profileManager: BaseInspectionProfileManager): InspectionProfileImpl {
    val profile = InspectionProfileImpl(baseProfile.name, inspectionToolsSupplier, profileManager, baseProfile, null)
    profile.initInspectionTools()
    profile.tools.forEach { targetTool ->
      val sourceTool = baseProfile.getToolsOrNull(targetTool.shortName, null) ?: return@forEach
      copyFrom(sourceTool, targetTool)
      val groupTool = sourceTool.defaultState.tool.tool as? DynamicGroupTool
      groupTool?.children?.forEach { childTool -> profile.addTool(null, childTool, null) }
    }
    return profile
  }

  fun writeXmlOptions(element: Element, options: Map<String, String>) {
    options.forEach { (key, value) ->
      val child = Element("option")
      element.addContent(child)
      child.setAttribute("name", key)
      child.setAttribute("value", value)
    }
  }
}