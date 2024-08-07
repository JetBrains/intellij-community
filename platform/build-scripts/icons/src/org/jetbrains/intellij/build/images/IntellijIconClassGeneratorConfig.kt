// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import org.jetbrains.jps.model.module.JpsModule

class IntellijIconClassGeneratorConfig : IconClasses() {
  override val modules: List<JpsModule>
    get() = super.modules.filterNot {
      // TODO: use icon-robots.txt
      it.name.startsWith("fleet")
    }

  override fun getConfigForModule(moduleName: String): IntellijIconClassGeneratorModuleConfig? {
    @Suppress("SpellCheckingInspection")
    return when (moduleName) {
      "intellij.devkit" -> IntellijIconClassGeneratorModuleConfig(
        className = "DevKitIcons",
        packageName = "org.jetbrains.idea.devkit",
      )
      // force generating "Groovy" inner class to preserve backward compatiblity
      "intellij.groovy.psi" -> IntellijIconClassGeneratorModuleConfig(className = "JetgroovyIcons", iconDirectory = "icons")
      "intellij.clouds.docker" -> IntellijIconClassGeneratorModuleConfig(className = "DockerIcons", packageName = "com.intellij.docker")
      "intellij.clouds.docker.gateway" -> IntellijIconClassGeneratorModuleConfig(className = "DockerGatewayIcons", packageName = "com.intellij.clouds.docker.gateway")
      "intellij.css" -> IntellijIconClassGeneratorModuleConfig(
        className = "CssIcons",
        packageName = "com.intellij.lang.css",
        iconDirectory = "icons/css",
      )
      "intellij.platform.split" -> IntellijIconClassGeneratorModuleConfig(
        className = "CwmCommonIcons",
        packageName = "com.intellij.cwm.common.icons",
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

      "intellij.python.parser" -> IntellijIconClassGeneratorModuleConfig(
        className = "PythonParserIcons",
        packageName = "com.jetbrains.python.parser.icons",
        iconDirectory = "icons/com/jetbrains/python/parser",
      )

      "intellij.python.psi" -> IntellijIconClassGeneratorModuleConfig(
        className = "PythonPsiApiIcons",
        packageName = "com.jetbrains.python.psi.icons",
        iconDirectory = "icons/com/jetbrains/python/psi",
      )


      "intellij.python" -> IntellijIconClassGeneratorModuleConfig(
        className = "PythonUltimateIcons",
        packageName = "com.intellij.python.pro.icons",
        iconDirectory = "icons/com/intellij/python/pro",
      )

      "intellij.python.community.impl"-> IntellijIconClassGeneratorModuleConfig(
        className = "PythonIcons",
        packageName = "com.jetbrains.python.icons",
        iconDirectory = "icons/com/jetbrains/pythonCore",
      )

      "intellij.notebooks.jupyter.core"-> IntellijIconClassGeneratorModuleConfig(
        className = "JupyterCoreIcons",
        packageName = "com.intellij.notebooks.jupyter.core.icons",
        iconDirectory = "icons/org.jetbrains.plugins.notebooks.jupyter",
      )

        "intellij.spring.mvc.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringMvcApiIcons",
        packageName = "com.intellij.spring.mvc",
      )

      "intellij.spring.boot" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringBootApiIcons",
        packageName = "com.intellij.spring.boot",
      )

      // default name 'com.goide.GOIcons' clashes with existing 'com.goide.GoIcons'
      "intellij.go.frontback" -> IntellijIconClassGeneratorModuleConfig(className = "GoGeneratedIcons", packageName = "com.goide")
      "intellij.toml.core" -> IntellijIconClassGeneratorModuleConfig(className = "TomlIcons", packageName = "org.toml")
      "intellij.markdown" -> IntellijIconClassGeneratorModuleConfig(className = "MarkdownIcons",
                                                                         packageName = "org.intellij.plugins.markdown")

      "intellij.grazie.core" -> IntellijIconClassGeneratorModuleConfig(className = "GrazieIcons", packageName = "com.intellij.grazie.icons")
      "intellij.django.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "DjangoIcons",
        packageName = "com.jetbrains.django"
      )
      "intellij.jinja" -> IntellijIconClassGeneratorModuleConfig(
        className = "Jinja2Icons",
        packageName = "com.intellij.jinja"
      )

      "intellij.bigdatatools.visualisation" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsVisualisationIcons",
                                                                                      packageName = "com.intellij.bigdatatools.visualization")
      "intellij.bigdatatools.core" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsCoreIcons",
                                                                             packageName = "com.jetbrains.bigdatatools.common")
      "intellij.swagger.core" -> IntellijIconClassGeneratorModuleConfig(className = "SwaggerCoreIcons",
                                                                        packageName = "com.intellij.swagger.core")
      "intellij.ml.llm.core" -> IntellijIconClassGeneratorModuleConfig(className = "MLLlmIcons", packageName = "com.intellij.ml.llm.core")
      "intellij.llmInstaller" -> IntellijIconClassGeneratorModuleConfig(className = "LLMIcons", packageName = "com.intellij.llmInstaller")

      "intellij.dts" -> IntellijIconClassGeneratorModuleConfig(className = "DtsIcons", packageName = "com.intellij.dts")

      "intellij.protoeditor.core" -> IntellijIconClassGeneratorModuleConfig(className = "ProtoeditorCoreIcons", packageName = "com.intellij.protobuf")

      "intellij.ide.startup.importSettings" -> IntellijIconClassGeneratorModuleConfig(
        className = "StartupImportIcons",
        packageName = "com.intellij.ide.startup.importSettings"
      )

      "intellij.pest" -> IntellijIconClassGeneratorModuleConfig(
        className = "PestIcons",
        packageName = "com.pestphp.pest"
      )

      "intellij.vcs.github" -> IntellijIconClassGeneratorModuleConfig(
        className = "GithubIcons",
        packageName = "org.jetbrains.plugins.github"
      )

      "intellij.javaee.jpabuddy.jpabuddy" -> IntellijIconClassGeneratorModuleConfig(
        className = "JpbIcons",
        packageName = "com.intellij.jpb"
      )

      "intellij.javaee.jpa.jpb.model" -> IntellijIconClassGeneratorModuleConfig(
        className = "JpaIcons",
        packageName = "com.intellij.jpa.jpb.model"
      )

      else -> super.getConfigForModule(moduleName)
    }
  }
}