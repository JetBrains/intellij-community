// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

// the same as ULTIMATE/build/src/org/jetbrains/intellij/build/ModulesXml.kt
@Suppress("CanConvertToMultiDollarString")
data class ModulesXml(val projectHome: Path, val modules: List<Path>) {
  companion object {
    fun getModulesXmlPath(projectHome: Path): Path = projectHome.resolve(".idea/modules.xml")

    fun readFromProject(projectHome: Path): ModulesXml {
      val modules = mutableListOf<Path>()

      val modulesXmlFile = getModulesXmlPath(projectHome)
      val root = JDOMUtil.load(modulesXmlFile)
      val componentElement = JDomSerializationUtil.findComponent(root, "ProjectModuleManager")
                             ?: error("$modulesXmlFile: missing ProjectModuleManager component")
      JDOMUtil.getChildren(componentElement.getChild("modules"), "module").forEach { moduleElement ->
        val (modulePathString, _) = listOf("filepath", "fileurl").map {
          moduleElement.getAttributeValue(it)?.replace("\$PROJECT_DIR$", projectHome.invariantSeparatorsPathString)
          ?: error("$modulesXmlFile: missing '$it' attribute")
        }

        modules.add(Path.of(modulePathString))
      }

      return ModulesXml(projectHome = projectHome, modules = modules)
    }
  }
}