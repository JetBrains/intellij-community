// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.android

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.buildSuggestionIfNeeded
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.EditorNotificationPanel
import java.util.function.Function

private const val ANDROID_PLUGIN_NAME: String = "Android"
private const val ANDROID_SUGGESTION_DISMISSED_KEY: String = "android.suggestion.dismissed"
private val ANDROID_PLUGIN_IDS: List<String> = listOf("org.jetbrains.android", "com.android.tools.design")

internal class AndroidSuggestionProvider : PluginSuggestionProvider {
  override fun getSuggestion(project: Project, file: VirtualFile): Function<FileEditor, EditorNotificationPanel?>? {
    val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
    if (!isAndroidXml(xmlFile)) {
      return null
    }

    return buildSuggestionIfNeeded(project,
                                   pluginIds = ANDROID_PLUGIN_IDS,
                                   pluginName = ANDROID_PLUGIN_NAME,
                                   fileLabel = "Android",
                                   suggestionDismissKey = ANDROID_SUGGESTION_DISMISSED_KEY)
  }

  private fun isAndroidXml(file: XmlFile): Boolean {
    val rootTag = file.rootTag ?: return false

    return rootTag.localNamespaceDeclarations.any {
      it.key == "android" && (it.value ?: "").contains("://schemas.android.com/")
    }
  }
}