// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import org.jetbrains.jps.model.module.JpsModule

class IntellijIconClassGeneratorConfig : IconsClasses() {
  override val modules: List<JpsModule>
    get() = super.modules.filterNot {
      // TODO: use icon-robots.txt
      it.name.startsWith("fleet")
    }

  override fun getConfigForModule(moduleName: String): IntellijIconClassGeneratorModuleConfig? {
    @Suppress("SpellCheckingInspection")
    return when (moduleName) {
      // force generating "Groovy" inner class to preserve backward compatiblity
      "intellij.groovy.psi" -> IntellijIconClassGeneratorModuleConfig(className = "JetgroovyIcons", iconDirectory = "icons")
      "intellij.clouds.docker" -> IntellijIconClassGeneratorModuleConfig(className = "DockerIcons", packageName = "com.intellij.docker")
      "intellij.struts2.ognl" -> IntellijIconClassGeneratorModuleConfig(
        className = "OgnlIcons",
        packageName = "com.intellij.lang.ognl",
        iconDirectory = "icons",
      )
      "intellij.struts2.dom" -> IntellijIconClassGeneratorModuleConfig(
        className = "Struts2Icons",
        packageName = "com.intellij.struts2",
        iconDirectory = "icons",
      )
      "intellij.css" -> IntellijIconClassGeneratorModuleConfig(
        className = "CssIcons",
        packageName = "com.intellij.lang.css",
        iconDirectory = "icons/css",
      )
      "intellij.properties.psi" -> IntellijIconClassGeneratorModuleConfig(
        className = "PropertiesIcons",
        packageName = "com.intellij.lang.properties",
        iconDirectory = "icons",
      )
      "intellij.spring" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringApiIcons",
        packageName = "com.intellij.spring",
      )
      "intellij.spring.mvc.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringMvcApiIcons",
        packageName = "com.intellij.spring.mvc",
      )
      "intellij.spring.persistence" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringPersistenceIntegrationIcons",
        packageName = "com.intellij.spring.persistence.integration",
      )
      // default name 'com.goide.GOIcons' clashes with existing 'com.goide.GoIcons'
      "intellij.go.impl" -> IntellijIconClassGeneratorModuleConfig(className = "GoGeneratedIcons", packageName = "com.goide")
      "intellij.toml.core" -> IntellijIconClassGeneratorModuleConfig(className = "TomlIcons", packageName = "org.toml")
      "intellij.markdown.core" -> IntellijIconClassGeneratorModuleConfig(className = "MarkdownIcons", packageName = "org.intellij.plugins.markdown")
      else -> super.getConfigForModule(moduleName)
    }
  }
}