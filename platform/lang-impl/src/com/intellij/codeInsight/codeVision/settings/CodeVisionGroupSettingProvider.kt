// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.BundleBase
import com.intellij.codeInsight.codeVision.CodeVisionMessageBundle
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.IdentityHashMap

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

  @ApiStatus.Internal
  object EP {
    val EXTENSION_POINT_NAME: ExtensionPointName<CodeVisionGroupSettingProvider> =
      ExtensionPointName.create("com.intellij.config.codeVisionGroupSettingProvider")

    fun findGroupModels(): List<CodeVisionGroupSettingProvider> {
      val extensionsByGroup = EXTENSION_POINT_NAME.extensionList.groupBy { it.groupId }
      val duplicates = extensionsByGroup.filterValues { it.size > 1 }
      if (duplicates.isNotEmpty()) {
        val message = "Multiple CodeLensGroupSettingProvider with same groupId are registered (${duplicates.keys})"
        logger<CodeVisionGroupSettingProvider>().error(
          message,
          Throwable(message),
          Attachment("duplicate-code-vision-groups.txt", describeDuplicateGroups(duplicates)).apply { isIncluded = true }
        )
      }

      return extensionsByGroup.values.map { it.first() }
    }

    private fun describeDuplicateGroups(duplicates: Map<String, List<CodeVisionGroupSettingProvider>>): String {
      val descriptors = IdentityHashMap<CodeVisionGroupSettingProvider, MutableList<PluginDescriptor>>()
      for (extensions in duplicates.values) {
        for (extension in extensions) {
          descriptors[extension] = mutableListOf()
        }
      }
      EXTENSION_POINT_NAME.processWithPluginDescriptor { extension, descriptor ->
        descriptors[extension]?.add(descriptor)
      }

      return buildString {
        for ((groupId, extensions) in duplicates) {
          appendLine("groupId=$groupId, count=${extensions.size}")
          for ((index, extension) in extensions.withIndex()) {
            appendLine("  provider=${identity(extension)}, kept=${index == 0}")
            appendLine("    classLoader=${identity(extension.javaClass.classLoader)}")
            val registrations = descriptors.getValue(extension)
            if (registrations.isEmpty()) {
              appendLine("    Registration descriptor is unavailable in the current extension snapshot.")
            }
            for (descriptor in registrations) {
              appendLine("    pluginId=${descriptor.pluginId}, version=${descriptor.version}, bundled=${descriptor.isBundled}")
              appendLine("    descriptor=$descriptor")
              appendLine("    pluginClassLoader=${identity(descriptor.pluginClassLoader)}")
            }
          }
        }
      }
    }

    private fun identity(value: Any?): String {
      return if (value == null) "null" else "${value.javaClass.name}@${Integer.toHexString(System.identityHashCode(value))}"
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
