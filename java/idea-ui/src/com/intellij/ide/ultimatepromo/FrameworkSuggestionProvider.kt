// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ultimatepromo

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.library.JavaLibraryUtil.hasLibraryJar
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.ideaUltimate
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.tryUltimate
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import java.util.function.Function

private const val APPLICATION_PROPERTIES: String = "application.properties"
private const val APPLICATION_YAML: String = "application.yaml"
private const val APPLICATION_YML: String = "application.yml"

private const val SPRING_BOOT_MAVEN: String = "org.springframework.boot:spring-boot"
private const val MICRONAUT_MAVEN: String = "io.micronaut:micronaut-core"
private const val QUARKUS_MAVEN: String = "io.quarkus:quarkus-core"
private const val KTOR_MAVEN: String = "io.ktor:ktor-http"

internal class FrameworkSuggestionProvider : PluginSuggestionProvider {
  override fun getSuggestion(project: Project, file: VirtualFile): Function<FileEditor, EditorNotificationPanel?>? {
    if (!isApplicationConfig(file.name)) return null

    val thisProductCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode
    if (thisProductCode == "IU") return null

    val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
    val framework = detectFramework(module) ?: return null

    if (isPluginSuggestionDismissed(framework)) return null

    return FrameworkPluginSuggestion(project, framework)
  }

  private fun isApplicationConfig(fileName: @NlsSafe String): Boolean {
    return fileName == APPLICATION_PROPERTIES || fileName == APPLICATION_YAML || fileName == APPLICATION_YML
  }
}

private class FrameworkPluginSuggestion(val project: Project, val framework: Framework) : Function<FileEditor, EditorNotificationPanel?> {
  override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
    val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Promo)
    panel.text = IdeBundle.message("plugins.advertiser.framework.supported.in.ultimate", framework.name, ideaUltimate.name)

    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.try.ultimate", ideaUltimate.name)) {
      val pluginId = PluginId.getId(framework.pluginId)
      tryUltimate(pluginId, ideaUltimate, project)
    }

    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
      FUSEventSource.EDITOR.logIgnoreExtension(project)
      dismissPluginSuggestion(framework)
      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    return panel
  }
}

private fun detectFramework(module: Module): Framework? {
  return when {
    hasLibraryJar(module, SPRING_BOOT_MAVEN) -> Framework("spring.boot", "com.intellij.spring.boot", "Spring Boot")
    hasLibraryJar(module, MICRONAUT_MAVEN) -> Framework("micronaut", "com.intellij.micronaut", "Micronaut")
    hasLibraryJar(module, QUARKUS_MAVEN) -> Framework("quarkus", "com.intellij.quarkus", "Quarkus")
    hasLibraryJar(module, KTOR_MAVEN) -> Framework("ktor", "intellij.ktor", "Ktor")
    else -> null
  }
}

private const val FRAMEWORK_SUGGESTION_DISMISSED_PREFIX: String = "promo.framework.suggestion.dismissed."

private fun dismissPluginSuggestion(framework: Framework) {
  PropertiesComponent.getInstance().setValue(FRAMEWORK_SUGGESTION_DISMISSED_PREFIX + framework.key, true)
}

private fun isPluginSuggestionDismissed(framework: Framework): Boolean {
  return PropertiesComponent.getInstance().isTrueValue(FRAMEWORK_SUGGESTION_DISMISSED_PREFIX + framework.key)
}

private class Framework(
  val key: String,
  val pluginId: String,
  val name: String
)