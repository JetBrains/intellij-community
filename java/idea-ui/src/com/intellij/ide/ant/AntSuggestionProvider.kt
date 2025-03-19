// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ant

import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.buildSuggestionIfNeeded
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile

private const val BUILD_XML_NAME = "build.xml"
private const val ANT_PLUGIN_NAME = "Ant"
private const val ANT_PLUGIN_ID = "AntSupport"
private const val ANT_SUGGESTION_DISMISSED_KEY = "ant.suggestion.dismissed"

internal class AntSuggestionProvider : PluginSuggestionProvider {
  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    if (file.name != BUILD_XML_NAME
        || !isAntBuild(project, file)) {
      return null
    }

    return buildSuggestionIfNeeded(project,
                                   pluginId = ANT_PLUGIN_ID,
                                   pluginName = ANT_PLUGIN_NAME,
                                   fileLabel = "Ant",
                                   suggestionDismissKey = ANT_SUGGESTION_DISMISSED_KEY)
  }

  private fun isAntBuild(project: Project, file: VirtualFile): Boolean {
    val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return false

    val rootTag = xmlFile.rootTag
    if (rootTag?.name != "project") return false

    return rootTag.getAttribute("default") != null
  }
}