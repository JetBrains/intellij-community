// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.DynamicGroupTool
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolsSupplier
import com.intellij.codeInspection.ex.ToolsImpl
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import org.jdom.Element
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.introspector.Property
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer

internal object YamlProfileUtils {

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

  fun makeYaml(): Yaml {
    val constr = CustomClassLoaderConstructor(YamlInspectionProfileRaw::class.java, YamlInspectionProfileRaw::class.java.classLoader,
                                              LoaderOptions())
    val yaml = Yaml(constr, representer)
    yaml.setBeanAccess(BeanAccess.FIELD)
    return yaml
  }

  private val representer: Representer get() = object : Representer(DumperOptions()) {
    init {
      propertyUtils.isSkipMissingProperties = true
    }
    override fun representJavaBeanProperty(javaBean: Any?, property: Property?, propertyValue: Any?, customTag: Tag?): NodeTuple? {
      // if value of property is null, ignore it.
      return if (propertyValue == null) {
        null
      }
      else {
        super.representJavaBeanProperty(javaBean, property, propertyValue, customTag)
      }
    }
  }

}