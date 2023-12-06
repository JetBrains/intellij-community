// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.LanguageLevelProjectExtension


internal class JavaModuleDataService : AbstractProjectDataService<JavaModuleData, Project?>() {
  override fun getTargetDataKey(): Key<JavaModuleData> = JavaModuleData.KEY

  override fun importData(
    toImport: Collection<DataNode<JavaModuleData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    if (projectData == null) return
    for (javaModuleNode in toImport) {
      val moduleNode = javaModuleNode.getParent(ModuleData::class.java) ?: continue
      val module = moduleNode.getUserData(AbstractModuleDataService.MODULE_KEY) ?: continue
      val javaModuleData = javaModuleNode.data

      importLanguageLevel(module, javaModuleData, modelsProvider)
      importTargetBytecodeVersion(module, javaModuleData)
    }
  }

  private fun importLanguageLevel(module: Module, javaModuleData: JavaModuleData, modelsProvider: IdeModifiableModelsProvider) {
    val modifiableRootModel = modelsProvider.getModifiableRootModel(module)
    val languageLevelProjectExtension = LanguageLevelProjectExtension.getInstance(module.project)
    val languageLevelModuleExtension = modifiableRootModel.getModuleExtension(LanguageLevelModuleExtension::class.java)
    val languageLevel = javaModuleData.languageLevel?.let { JavaProjectDataServiceUtil.adjustLevelAndNotify(module.project, it) }
    val projectLanguageLevel = languageLevelProjectExtension.languageLevel
    languageLevelModuleExtension.languageLevel =
      when (languageLevel) {
        projectLanguageLevel -> null
        else -> languageLevel
      }
  }

  private fun importTargetBytecodeVersion(module: Module, javaModuleData: JavaModuleData) {
    val compilerConfiguration = CompilerConfiguration.getInstance(module.project)
    val projectTargetBytecodeVersion = compilerConfiguration.projectBytecodeTarget
    val targetBytecodeVersion = javaModuleData.targetBytecodeVersion
    compilerConfiguration.setBytecodeTargetLevel(module, when (targetBytecodeVersion) {
      projectTargetBytecodeVersion -> null
      else -> targetBytecodeVersion
    })
  }
}