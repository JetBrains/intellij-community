// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager

private class InspectionsProfileConfigurationHandler: ConfigurationHandler {
  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    if (configuration.find("inspections") !is List<*>) return

    val gradleProfileName = "Gradle Imported"
    val profileManager = ProjectInspectionProfileManager.getInstance(project)

    val importedProfile = InspectionProfileImpl(gradleProfileName, InspectionToolRegistrar.getInstance(), profileManager)
    importedProfile.copyFrom(profileManager.getProfile(InspectionProfile.DEFAULT_PROFILE_NAME))
    importedProfile.initInspectionTools(project)

    val modifiableModel = InspectionProfileModifiableModel(importedProfile)
    modifiableModel.name = gradleProfileName
    modifiableModel.commit()

    profileManager.addProfile(importedProfile)
    profileManager.setRootProfile(gradleProfileName)
  }
}
