// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel


internal class JavaProjectDataService : AbstractProjectDataService<JavaProjectData, Project?>() {

  override fun getTargetDataKey(): Key<JavaProjectData> = JavaProjectData.KEY

  override fun importData(
    toImport: Collection<DataNode<JavaProjectData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    if (toImport.isEmpty() || projectData == null) return
    if (!ExternalSystemApiUtil.isOneToOneMapping(project, projectData, modelsProvider.modules)) return

    val javaProjectData = toImport.single().data
    ExternalSystemApiUtil.executeProjectChangeAction(project) {
      importLanguageLevel(project, javaProjectData)
      importTargetBytecodeVersion(project, javaProjectData)
      importCompilerArguments(project, javaProjectData)
    }
  }

  private fun importLanguageLevel(project: Project, javaProjectData: JavaProjectData) {
    val projectRootManager = ProjectRootManager.getInstance(project)
    val projectSdk = projectRootManager.projectSdk
    val projectSdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(projectSdk)
    val projectSdkLanguageLevel = projectSdkVersion?.maxLanguageLevel
    val languageLevel = JavaProjectDataServiceUtil.adjustLevelAndNotify(project, javaProjectData.languageLevel)

    val languageLevelProjectExtension = LanguageLevelProjectExtension.getInstance(project)
    languageLevelProjectExtension.languageLevel = languageLevel
    languageLevelProjectExtension.default = languageLevel == projectSdkLanguageLevel
  }

  private fun importTargetBytecodeVersion(project: Project, javaProjectData: JavaProjectData) {
    val compilerConfiguration = CompilerConfiguration.getInstance(project)
    val targetBytecodeVersion = javaProjectData.targetBytecodeVersion
    compilerConfiguration.projectBytecodeTarget = targetBytecodeVersion
  }

  private fun importCompilerArguments(project: Project, javaProjectData: JavaProjectData) {
    val compilerConfiguration = CompilerConfiguration.getInstance(project)
    val compilerArguments = javaProjectData.compilerArguments
    compilerConfiguration.additionalOptions = compilerArguments
  }
}

internal object JavaProjectDataServiceUtil {

  internal fun adjustLevelAndNotify(project: Project, level: LanguageLevel): LanguageLevel {
    if (!AcceptedLanguageLevelsSettings.isLanguageLevelAccepted(level)) {
      val highestAcceptedLevel = AcceptedLanguageLevelsSettings.getHighestAcceptedLevel()
      if (highestAcceptedLevel.isLessThan(level)) {
        AcceptedLanguageLevelsSettings.showNotificationToAccept(project, level)
      }
      return if (highestAcceptedLevel.isAtLeast(level)) AcceptedLanguageLevelsSettings.getHighest() else highestAcceptedLevel
    }
    return level
  }
}