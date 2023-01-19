/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.externalSystem

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel


class JavaProjectDataService : AbstractProjectDataService<JavaProjectData, Project?>() {

  override fun getTargetDataKey(): Key<JavaProjectData> = JavaProjectData.KEY

  override fun importData(
    toImport: Collection<DataNode<JavaProjectData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    if (toImport.isEmpty() || projectData == null) return
    require(toImport.size == 1) { String.format("Expected to get a single project but got %d: %s", toImport.size, toImport) }
    if (!ExternalSystemApiUtil.isOneToOneMapping(project, projectData, modelsProvider.modules)) return
    val javaProjectData = toImport.first().data

    ExternalSystemApiUtil.executeProjectChangeAction(object : DisposeAwareProjectChange(project) {
      override fun execute() {
        importProjectSdk(project, javaProjectData)
        importLanguageLevel(project, javaProjectData)
        importTargetBytecodeVersion(project, javaProjectData)
      }
    })
  }

  private fun importProjectSdk(project: Project, javaProjectData: JavaProjectData) {
    if (!javaProjectData.isSetJdkVersion) return
    val jdkVersion = javaProjectData.jdkVersion
    val sdk = JavaSdkVersionUtil.findJdkByVersion(jdkVersion)
    val projectRootManager = ProjectRootManager.getInstance(project)
    val projectSdk = projectRootManager.projectSdk
    if (projectSdk == null) {
      projectRootManager.projectSdk = sdk
    }
  }

  private fun importLanguageLevel(project: Project, javaProjectData: JavaProjectData) {
    val projectRootManager = ProjectRootManager.getInstance(project)
    val projectSdk = projectRootManager.projectSdk
    val projectSdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(projectSdk)
    val projectSdkLanguageLevel = projectSdkVersion?.maxLanguageLevel
    val languageLevel = adjustLevelAndNotify(project, javaProjectData.languageLevel)

    val languageLevelProjectExtension = LanguageLevelProjectExtension.getInstance(project)
    languageLevelProjectExtension.languageLevel = languageLevel
    languageLevelProjectExtension.default = languageLevel == projectSdkLanguageLevel
  }


  private fun importTargetBytecodeVersion(project: Project, javaProjectData: JavaProjectData) {
    val compilerConfiguration = CompilerConfiguration.getInstance(project)
    val targetBytecodeVersion = javaProjectData.targetBytecodeVersion
    compilerConfiguration.projectBytecodeTarget = targetBytecodeVersion
  }

  companion object {

    internal fun adjustLevelAndNotify(project: Project, level: LanguageLevel): LanguageLevel {
      if (!AcceptedLanguageLevelsSettings.isLanguageLevelAccepted(level)) {
        val highestAcceptedLevel = AcceptedLanguageLevelsSettings.getHighestAcceptedLevel()
        if (highestAcceptedLevel.isLessThan(level)) {
          AcceptedLanguageLevelsSettings.showNotificationToAccept(project, level)
        }
        return if (highestAcceptedLevel.isAtLeast(level)) LanguageLevel.HIGHEST else highestAcceptedLevel
      }
      return level
    }
  }
}