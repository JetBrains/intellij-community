// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.promo

import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile

internal class LiquibaseSuggestionProvider : PluginSuggestionProvider {
  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
    if (!isLiquibaseFile(xmlFile)) return null

    val framework = Framework("liquibase", "com.intellij.liquibase", "Liquibase")
    if (isPluginSuggestionDismissed(framework)) return null

    return FrameworkPluginSuggestion(project, framework)
  }

  private fun isLiquibaseFile(xmlFile: XmlFile): Boolean {
    val rootTag = xmlFile.rootTag
    return rootTag?.name == "databaseChangeLog"
  }
}