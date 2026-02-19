// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.BundleBase
import com.intellij.codeInsight.codeVision.CodeVisionMessageBundle
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

interface CodeVisionGroupSettingProvider {
  /**
   * Group id settings refer to. @see [CodeVisionProvider.groupId]
   */
  val groupId: String

  /**
   * Name that is shown in settings
   */
  @get:Nls
  val groupName: String
    get() = CodeVisionMessageBundle.message("codeLens.${groupId}.name")

  @get:Nls
  val description: String
    get() = getLanguageSpecificDescriptionOrDefault()

  private fun getLanguageSpecificDescriptionOrDefault(): @Nls String {
    val languageId = previewLanguage?.id ?: return defaultDescription
    val ep = CodeVisionSettingsPreviewLanguage.EP_NAME.extensionList.firstOrNull { it.language == languageId } ?: return defaultDescription
    val bundle = ep.findBundle() ?: return defaultDescription
    return BundleBase.messageOrDefault(bundle, "$languageId.codeLens.$groupId.description", defaultDescription)!!
  }

  private val defaultDescription: @Nls String
    get() = CodeVisionMessageBundle.message("codeLens.${groupId}.description")

  private val previewLanguage: Language?
    get() {
      val primaryIdeLanguages = IdeLanguageCustomization.getInstance().primaryIdeLanguages
      return CodeVisionSettingsPreviewLanguage.EP_NAME.extensionList.asSequence()
               .filter { it.modelId == groupId }
               .map { Language.findLanguageByID(it.language) }
               .sortedBy { language -> primaryIdeLanguages.indexOf(language).takeIf { it != -1 } ?: Integer.MAX_VALUE }
               .firstOrNull()
             ?: Language.findLanguageByID("JAVA")
    }

  fun createModel(project: Project): CodeVisionGroupSettingModel {
    val providers = CodeVisionProviderFactory.createAllProviders(project).filter { it.groupId == groupId }
    val settings = CodeVisionSettings.getInstance()
    val isEnabled = settings.codeVisionEnabled && settings.isProviderEnabled(groupId)
    return createSettingsModel(isEnabled, providers)
  }

  fun createSettingsModel(isEnabled: Boolean, providers: List<CodeVisionProvider<*>>): CodeVisionGroupSettingModel {
    return CodeVisionGroupDefaultSettingModel(groupName, groupId, description, previewLanguage, isEnabled, providers)
  }

  object EP {
    val EXTENSION_POINT_NAME: ExtensionPointName<CodeVisionGroupSettingProvider> =
      ExtensionPointName.create("com.intellij.config.codeVisionGroupSettingProvider")

    fun findGroupModels(): List<CodeVisionGroupSettingProvider> {
      val extensions = EXTENSION_POINT_NAME.extensions
      val distinctExtensions = extensions.distinctBy { it.groupId }
      if (extensions.size != distinctExtensions.size)
        logger<CodeVisionGroupSettingProvider>().error("Multiple CodeLensGroupSettingProvider with same groupId are registered")

      return distinctExtensions
    }

    /**
     * Find all registered [CodeVisionProvider] without [CodeVisionGroupSettingProvider]
     */
    fun findSingleModels(project: Project): List<CodeVisionGroupSettingProvider> {
      val registeredGroupIds = EXTENSION_POINT_NAME.extensions.distinctBy { it.groupId }.map { it.groupId }
      val providersWithoutGroup = CodeVisionProviderFactory.createAllProviders(project).filter { registeredGroupIds.contains(it.groupId).not() }
      return providersWithoutGroup.map { CodeVisionUngroppedSettingProvider(it.groupId) }
    }
  }
}