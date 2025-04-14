// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.wiremock

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationPanel.Status
import com.intellij.ui.EditorNotifications

internal class WireMockSuggestionProvider : PluginSuggestionProvider {

  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    if (!FileTypeManager.getInstance().isFileOfType(file, JsonFileType.INSTANCE)) return null

    if (isPluginSuggestionDismissed() || tryUltimateIsDisabled()) return null

    val requiredPluginId = PluginId.getId(WIREMOCK_PLUGIN_ID)
    if (PluginManager.isPluginInstalled(requiredPluginId)) return null

    val thisProductCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode

    val isWireMockFile = detectWireMockStubs(project, file)
    if (!isWireMockFile) return null

    return WireMockPluginSuggestion(project, thisProductCode)
  }
}

private class WireMockPluginSuggestion(val project: Project,
                                       val thisProductCode: String) : PluginSuggestion {
  override val pluginIds: List<String> = listOf(WIREMOCK_PLUGIN_ID)

  override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
    val status = if (PluginAdvertiserService.isCommunityIde()) Status.Promo else Status.Info
    val panel = EditorNotificationPanel(fileEditor, status)

    val suggestedIdeCode = PluginAdvertiserService.getSuggestedCommercialIdeCode(thisProductCode)
    val suggestedCommercialIde = PluginAdvertiserService.getIde(suggestedIdeCode)

    if (suggestedCommercialIde == null) {
      panel.text = IdeBundle.message("plugins.advertiser.plugins.found", WIREMOCK_FILES)

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugin.name", WIREMOCK_PLUGIN_NAME)) {
        val pluginIds = listOf(WIREMOCK_PLUGIN_ID)

        FUSEventSource.EDITOR.logInstallPlugins(pluginIds, project)
        installAndEnable(project, pluginIds.map(PluginId::getId).toSet(), true) {
          EditorNotifications.getInstance(project).updateAllNotifications()
        }
      }
    }
    else {
      panel.text = IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", WIREMOCK_FILES, suggestedCommercialIde.name)
      panel.createTryUltimateActionLabel(suggestedCommercialIde, project, PluginId.getId(WIREMOCK_PLUGIN_ID))
    }

    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
      FUSEventSource.EDITOR.logIgnoreExtension(project)
      dismissPluginSuggestion()
      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    return panel
  }
}

private const val WIREMOCK_PLUGIN_ID: String = "com.intellij.wiremock"
private const val WIREMOCK_PLUGIN_NAME: String = "WireMock"
private const val WIREMOCK_FILES: String = "WireMock"
private const val WIREMOCK_SUGGESTION_DISMISSED_KEY: String = "wiremock.suggestion.dismissed"

private fun dismissPluginSuggestion() {
  PropertiesComponent.getInstance().setValue(WIREMOCK_SUGGESTION_DISMISSED_KEY, true)
}

private fun isPluginSuggestionDismissed(): Boolean {
  return PropertiesComponent.getInstance().isTrueValue(WIREMOCK_SUGGESTION_DISMISSED_KEY)
}

private fun detectWireMockStubs(project: Project, file: VirtualFile): Boolean {
  val psiFile = PsiManager.getInstance(project).findFile(file)
  return isWireMockJson(psiFile)
}

private fun isWireMockJson(file: PsiFile?): Boolean {
  if (file !is JsonFile) {
    return false
  }

  return CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result(looksLikeWireMockFile(file), file)
  }
}

private fun looksLikeWireMockFile(psiFile: JsonFile): Boolean {
  if (psiFile.parent is PsiDirectory
      && psiFile.parent?.name == "mappings"
      && (psiFile.topLevelValue as? JsonObject)?.findProperty("request") != null) {
    return true
  }

  val topLevelValue = psiFile.topLevelValue
  return topLevelValue is JsonObject && topLevelValue.findProperty("mappings")?.value is JsonArray
}