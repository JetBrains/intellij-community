// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.intellij.build.impl.PLATFORM_FIXED_JAR_MODULES
import org.jetbrains.intellij.build.impl.helpPluginXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BuiltInHelpPluginTest {
  @Test
  fun `help plugin requires the platform build that supplies its extension`() {
    val descriptor = JDOMUtil.load(helpPluginXml("263.7307.150.1", "263.7307.150", "IntelliJ IDEA"))
    assertEquals("263.7307.150", descriptor.getChild("idea-version").getAttributeValue("since-build"))
    val extensions = descriptor.getChild("extensions")
    assertNull(extensions.getChild("applicationService"))
    assertEquals("com.jetbrains.builtInHelp.BuiltInHelpManager",
                 extensions.getChild("helpTargetProvider").getAttributeValue("implementation"))
    assertEquals(2, extensions.getChildren("httpRequestHandler").size)
  }

  @Test
  fun `help descriptor declares dependencies outside the bootstrap jars`() {
    val module = JDOMUtil.load(BuildPaths.COMMUNITY_ROOT.communityRoot.resolve("plugins/built-in-help/intellij.builtInHelp.iml"))
    val bootstrapModules = PLATFORM_FIXED_JAR_MODULES.values.flatten().toSet()
    val dependencies = module.getChildren("component").flatMap { it.getChildren("orderEntry") }
      .filter { it.getAttributeValue("type") == "module" && it.getAttributeValue("scope") !in setOf("TEST", "PROVIDED") }
      .map { it.getAttributeValue("module-name") }.toSet() - bootstrapModules
    val descriptor = JDOMUtil.load(helpPluginXml("263.7307.150.1", "263.7307.150", "IntelliJ IDEA"))
    val declared = descriptor.getChild("dependencies").getChildren("module").map { it.getAttributeValue("name") }.toSet()
    assertEquals(dependencies, declared)
  }
}
