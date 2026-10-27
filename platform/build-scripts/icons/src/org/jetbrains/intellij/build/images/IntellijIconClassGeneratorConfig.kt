// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import org.jetbrains.jps.model.module.JpsModule

class IntellijIconClassGeneratorConfig : IconClasses() {
  override val modules: List<JpsModule>
    get() = super.modules.filterNot {
      // TODO: use icon-robots.txt
      it.name.startsWith("fleet")
    }

  override fun getConfigForModule(moduleName: String): IntellijIconClassGeneratorModuleConfig? {
    return when (moduleName) {
      "intellij.aopCommon" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.aop",
      )
      "intellij.apiWatcher" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.apiwatcher",
      )
      "intellij.astro" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "org.jetbrains.astro",
      )
      "intellij.cloudFormation" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.aws.cloudformation",
      )
      "intellij.go.template" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.goide.template",
      )
      "intellij.graalvm" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.graalvm",
      )
      "intellij.haml" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "org.jetbrains.plugins.haml",
      )
      "intellij.javaee.beanValidation" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.beanValidation",
      )
      "intellij.javaee.cdi" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.cdi",
      )
      "intellij.javaee.jsf" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.jsf",
      )
      "intellij.javascript.bun" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.javascript.bun",
      )
      "intellij.jboss.drools" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.plugins.drools",
      )
      "intellij.microservices.debugger" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.microservices.debugger",
      )
      "intellij.phpspec" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.jetbrains.php.phpspec",
      )
      "intellij.profiler.lineProfiler" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.profiler.lineProfiler",
      )
      "intellij.rider.plugins.playStation" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.jetbrains.rider.plugins.playStation",
      )
      "intellij.safe.push" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.safepush",
      )
      "intellij.slim" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "org.jetbrains.plugins.slim",
      )
      "intellij.spring.modulith" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.spring.modulith",
      )
      "intellij.terraform" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "org.intellij.terraform",
      )
      "intellij.vitejs" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.vite",
      )
      "intellij.webassembly" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.webassembly",
      )
      "intellij.wechat.miniprogram" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.wechat.miniprogram",
      )
      "intellij.devkit" -> IntellijIconClassGeneratorModuleConfig(
        className = "DevKitIcons",
        packageName = "org.jetbrains.idea.devkit",
      )
      "intellij.diagram.impl" -> IntellijIconClassGeneratorModuleConfig(
        className = "UmlIcons",
        packageName = "com.intellij.uml"
      )
      "intellij.platform.ide.ui.inspector" -> IntellijIconClassGeneratorModuleConfig(
        // inspection icons are loaded by com.intellij.internal.inspector.components.HierarchyTree.Icons
        excludePackages = listOf("com.intellij.internal.inspector.icons"),
      )
      "intellij.libraries.microba" -> IntellijIconClassGeneratorModuleConfig(
        excludePackages = listOf("com.michaelbaranov.microba.calendar.resource"),
      )
      // force generating "Groovy" inner class to preserve backward compatiblity
      "intellij.groovy.psi" -> IntellijIconClassGeneratorModuleConfig(className = "JetgroovyIcons", iconDirectory = "icons")
      "intellij.clouds.docker" -> IntellijIconClassGeneratorModuleConfig(
        className = "DockerIcons",
        packageName = "com.intellij.docker"
      )
      "intellij.cidr.debugger.dap" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.jetbrains.cidr.execution.debugger.backend.dap",
      )
      // The module has no Classic-UI icon anywhere, so a plain "merge" rule can't find a
      // common icon root (see ImageCollector.downToRoot). Point the collector straight at
      // the expui tree instead.
      "intellij.rider.cpp.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "RiderCppCoreIcons",
        packageName = "com.jetbrains.rider.cpp.icons",
        iconDirectory = "icons/expui",
      )
      "intellij.rider.cpp.core.languages" -> IntellijIconClassGeneratorModuleConfig(
        className = "RiderCppCoreLanguagesIcons",
        packageName = "com.jetbrains.rider.cpp.fileType.icons",
      )
      "intellij.clouds.docker.gateway" -> IntellijIconClassGeneratorModuleConfig(
        className = "DockerGatewayIcons",
        packageName = "com.intellij.clouds.docker.gateway"
      )
      "intellij.clouds.kubernetes" -> IntellijIconClassGeneratorModuleConfig(
        className = "KubernetesIcons",
        packageName = "com.intellij.kubernetes"
      )
      "intellij.clouds.telepresence.shared" -> IntellijIconClassGeneratorModuleConfig(
        className = "TelepresenceIcons",
        packageName = "com.intellij.clouds.telepresence.shared"
      )
      "intellij.css" -> IntellijIconClassGeneratorModuleConfig(
        className = "CssIcons",
        packageName = "com.intellij.css.common",
        iconDirectory = "icons/css",
      )
      "intellij.vuejs.common" -> IntellijIconClassGeneratorModuleConfig(
        className = "VuejsIcons",
        packageName = "org.jetbrains.vuejs",
        iconDirectory = "icons",
      )
      "intellij.remoteDev.icons" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.remoteDev.icons",
        className = "JetBrainsClientIcons"
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

      "intellij.python.community" -> IntellijIconClassGeneratorModuleConfig(
        className = "PythonIcons",
        packageName = "com.jetbrains.python.icons",
        iconDirectory = "icons/com/jetbrains/pythonCore",
      )
      "intellij.python.community.impl" -> IntellijIconClassGeneratorModuleConfig(
        // packaging icons are loaded by com.jetbrains.python.packaging.toolwindow.PyPackageIcons
        excludePackages = listOf("icons.com.jetbrains.python.packaging"),
      )

      "intellij.notebooks.jupyter.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "JupyterCoreIcons",
        packageName = "com.intellij.notebooks.jupyter.core.icons",
        iconDirectory = "icons/org.jetbrains.plugins.notebooks.jupyter",
      )

      "intellij.spring.mvc.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringMvcApiIcons",
        packageName = "com.intellij.spring.mvc",
      )

      "intellij.spring.boot.shared" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringBootApiIcons",
        packageName = "com.intellij.spring.boot",
      )

      // default name 'com.goide.GOIcons' clashes with existing 'com.goide.GoIcons'
      "intellij.go.frontback" -> IntellijIconClassGeneratorModuleConfig(className = "GoGeneratedIcons", packageName = "com.goide")
      "intellij.javascript.parser" -> IntellijIconClassGeneratorModuleConfig(
        className = "JavaScriptCoreIcons",
        packageName = "icons",
        iconDirectory = "icons",
      )
      "intellij.javascript.debugger.common" -> IntellijIconClassGeneratorModuleConfig(
        className = "JavaScriptDebuggerIcons",
        packageName = "com.intellij.javascript.debugger.common",
      )
      "intellij.toml.core" -> IntellijIconClassGeneratorModuleConfig(className = "TomlIcons", packageName = "org.toml")
      "intellij.markdown" -> IntellijIconClassGeneratorModuleConfig(
        className = "MarkdownIcons",
        packageName = "org.intellij.plugins.markdown"
      )

      "intellij.grazie.core" -> IntellijIconClassGeneratorModuleConfig(className = "GrazieIcons", packageName = "com.intellij.grazie.icons")
      "intellij.django.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "DjangoIcons",
        packageName = "com.jetbrains.django"
      )
      "intellij.jinja" -> IntellijIconClassGeneratorModuleConfig(
        className = "Jinja2Icons",
        packageName = "com.intellij.jinja"
      )

      "intellij.bigdatatools.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "BigdatatoolsCoreIcons",
        packageName = "com.jetbrains.bigdatatools.common"
      )
      "intellij.swagger.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "SwaggerCoreIcons",
        packageName = "com.intellij.swagger.core"
      )
      "intellij.ml.llm.core" -> IntellijIconClassGeneratorModuleConfig(className = "MLLlmIcons", packageName = "com.intellij.ml.llm")
      "intellij.ml.llm.nextEdits.frontend" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.ml.llm.nextEdits.frontend",
      )
      "intellij.llmInstaller" -> IntellijIconClassGeneratorModuleConfig(className = "LLMIcons", packageName = "com.intellij.llmInstaller")
      "intellij.llmInstaller.common" -> IntellijIconClassGeneratorModuleConfig(packageName = "com.intellij.llmInstaller.common")
      "intellij.llmInstaller.jupyter" -> IntellijIconClassGeneratorModuleConfig(packageName = "com.intellij.llmInstaller.jupyter")
      "intellij.llmInstaller.pycharm.community" -> IntellijIconClassGeneratorModuleConfig(
        packageName = "com.intellij.llmInstaller.pycharm.community",
      )

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

      "intellij.vcs.git.backend" -> IntellijIconClassGeneratorModuleConfig(
        className = "GitIcons",
        packageName = "com.intellij.vcs.git.icons"
      )

      "intellij.vcs.gitlab.shared" -> IntellijIconClassGeneratorModuleConfig(
        className = "GitlabIcons",
        packageName = "com.intellij.vcs.gitlab.icons"
      )

      "intellij.vcs.github" -> IntellijIconClassGeneratorModuleConfig(
        className = "GithubIcons",
        packageName = "org.jetbrains.plugins.github"
      )

      "intellij.vcs.perforce" -> IntellijIconClassGeneratorModuleConfig(
        className = "PerforceIcons",
        packageName = "org.jetbrains.idea.perforce",
      )

      "intellij.javaee.jpabuddy.jpabuddy" -> IntellijIconClassGeneratorModuleConfig(
        className = "JpbIcons",
        packageName = "com.intellij.jpb"
      )

      "intellij.javaee.jpa.jpb.model" -> IntellijIconClassGeneratorModuleConfig(
        className = "JpaIcons",
        packageName = "com.intellij.jpa.jpb.model"
      )

      "intellij.liquibase.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "LiquibaseIcons",
        packageName = "com.intellij.liquibase.core"
      )

      "intellij.flyway.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "FlywayIcons",
        packageName = "com.intellij.flyway.core"
      )

      "intellij.android.ml-api" -> IntellijIconClassGeneratorModuleConfig(
        className = "AndroidAIPluginIcons",
        packageName = "com.android.tools.idea.studiobot.icons",
        iconDirectory = "icons"
      )

      "intellij.platform.vcs.dvcs.impl.shared" -> IntellijIconClassGeneratorModuleConfig(
        className = "DvcsImplIcons",
        packageName = "icons",
      )

      "intellij.platform.icons" -> super.getConfigForModule(moduleName)?.copy(generateJewelIcons = true, generateNewApiKotlinIcons = true) ?: IntellijIconClassGeneratorModuleConfig(generateJewelIcons = true, generateNewApiKotlinIcons = true)

      "intellij.java.ultimate.icons" -> IntellijIconClassGeneratorModuleConfig(
        className = "JavaUltimateIcons",
        packageName = "com.intellij.java.ultimate.icons",
      )

      "intellij.uiDesigner" -> IntellijIconClassGeneratorModuleConfig(
        className = "UiDesignerIcons",
        packageName = "com.intellij.designer",
      )

      "intellij.r.psi" -> IntellijIconClassGeneratorModuleConfig(
        className = "RIcons",
        packageName = "com.intellij.r.psi.icons",
      )

      "intellij.platform.debugger.impl" -> IntellijIconClassGeneratorModuleConfig(
        className = "PlatformDebuggerImplIcons",
        packageName = "icons",
        iconDirectory = "icons"
      )

      "intellij.postcss.paid" -> IntellijIconClassGeneratorModuleConfig(
        className = "PostCssIcons",
        packageName = "org.intellij.plugins.postcss",
      )

      "intellij.platform.problemView.security" -> IntellijIconClassGeneratorModuleConfig(
        className = "SecurityProblemsViewIcons",
        packageName = "com.intellij.platform.problemView.security.icons",
      )

      else -> super.getConfigForModule(moduleName)
    }
  }
}
