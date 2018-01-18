/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

/**
 * Created by Nikita.Skvortsov
 * date: 18.09.2017.
 */
class CodeStyleConfigurationHandler: ConfigurationHandler {

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val importedSchemeName = "Gradle Imported"

    val codeStyleSettings: Map<String, *> = configuration.find("codeStyle") as? Map<String, *> ?: return
    val schemes = CodeStyleSchemes.getInstance()

    val existingScheme = schemes.findPreferredScheme(importedSchemeName)
    if (existingScheme.name == importedSchemeName) {
      schemes.deleteScheme(existingScheme)
    }

    val importedScheme = schemes.createNewScheme(importedSchemeName, schemes.defaultScheme)

    val styleSettings = importedScheme.codeStyleSettings
    // GLOBAL
    styleSettings.apply {
      (codeStyleSettings["USE_SAME_INDENTS"] as? Boolean)?.let { USE_SAME_INDENTS = it }
      (codeStyleSettings["RIGHT_MARGIN"] as? Number)?.toInt()?.let { defaultRightMargin = it }
      (codeStyleSettings["KEEP_CONTROL_STATEMENT_IN_ONE_LINE"] as? Boolean)?.let { KEEP_CONTROL_STATEMENT_IN_ONE_LINE = it }
    }


    val languages = listOf("java", "groovy")
    val importedLangs: Map<String,*> = (codeStyleSettings["languages"] as? Map<String,*>) ?: mapOf<String, Any?>()

    languages.forEach { langName ->
      (importedLangs[langName] as? Map<*, *>)?.let { langCfg ->
        val importer = CodeStyleImporterExtensionManager.importerForLang(langName) ?: return@let
        importCommonSettings(styleSettings.getCommonSettings(importer.language), langCfg)
        importer.processSettings(styleSettings.getCustomSettings(importer.customClass), langCfg)
      }
    }

    schemes.addScheme(importedScheme)
    schemes.currentScheme = importedScheme
  }

  private fun importCommonSettings(commonSettings: CommonCodeStyleSettings, langCfg: Map<*, *>) {
    commonSettings.apply {
      (langCfg["RIGHT_MARGIN"] as? Number)?.toInt()?.let { RIGHT_MARGIN = it }
      (langCfg["WRAP_COMMENTS"] as? Boolean)?.let { WRAP_COMMENTS = it }
      (langCfg["IF_BRACE_FORCE"] as? String)?.let { IF_BRACE_FORCE = ForceEnum.valueOf(it).index }
      (langCfg["DOWHILE_BRACE_FORCE"] as? String)?.let { DOWHILE_BRACE_FORCE = ForceEnum.valueOf(it).index }
      (langCfg["WHILE_BRACE_FORCE"] as? String)?.let { WHILE_BRACE_FORCE = ForceEnum.valueOf(it).index }
      (langCfg["FOR_BRACE_FORCE"] as? String)?.let { FOR_BRACE_FORCE = ForceEnum.valueOf(it).index }
      (langCfg["KEEP_CONTROL_STATEMENT_IN_ONE_LINE"] as? Boolean)?.let { KEEP_CONTROL_STATEMENT_IN_ONE_LINE = it }
    }
  }

  enum class ForceEnum(val index: Int) {  DO_NOT_FORCE(0), FORCE_BRACES_IF_MULTILINE(1), FORCE_BRACES_ALWAYS(3);  }
}

class CodeStyleImporterExtensionManager {
  companion object {
    fun importerForLang(langName: String): CodeStyleConfigurationImporter<CustomCodeStyleSettings>? =
      Extensions.getExtensions(CodeStyleConfigurationImporter.EP_NAME).firstOrNull { it.canImport(langName) }
  }
}