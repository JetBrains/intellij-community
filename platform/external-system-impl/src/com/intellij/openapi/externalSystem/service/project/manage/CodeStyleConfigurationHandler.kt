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
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.externalSystem.model.project.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSchemes

/**
 * Created by Nikita.Skvortsov
 * date: 18.09.2017.
 */
class CodeStyleConfigurationHandler: ConfigurationHandler {

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val importedSchemeName = "Gradle Imported"

    val codeStyleSettings: Map<String, *> = configuration.find("codeStyle") as? Map<String,*> ?: return
    val schemes = CodeStyleSchemes.getInstance()

    val existingScheme = schemes.findPreferredScheme(importedSchemeName)
    if (existingScheme.name == importedSchemeName) {
      schemes.deleteScheme(existingScheme)
    }

    val importedScheme = schemes.createNewScheme(importedSchemeName, schemes.defaultScheme)

    codeStyleSettings["indent"]?.let { indentType ->
      val indentOptions = importedScheme.codeStyleSettings.indentOptions ?: return@let
      when (indentType) {
        "tabs"   -> { indentOptions.USE_TAB_CHARACTER = true  }
        "spaces" -> { indentOptions.USE_TAB_CHARACTER = false }
        else     -> {} // warn?
      }
    }

    codeStyleSettings["indentSize"]?.let {
      val indentOptions = importedScheme.codeStyleSettings.indentOptions ?: return@let
      if (it is Number) {
        indentOptions.INDENT_SIZE = it.toInt()
      }
    }

    schemes.currentScheme = importedScheme
  }
}