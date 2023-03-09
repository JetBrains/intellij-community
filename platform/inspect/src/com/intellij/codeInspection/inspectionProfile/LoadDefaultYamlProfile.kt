// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager

/**
 * Internal action for debug purposes.
 * Action loads default yaml inspection profile from '.idea/inspectionProfile/profile.yml' file and sets loaded profile as a current one.
 */
class LoadDefaultYamlProfile : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val yamlProfile = YamlInspectionProfileImpl.loadFrom(project)
    val manager = ProjectInspectionProfileManager.getInstance(project)
    val profile = yamlProfile.buildEffectiveProfile()
    manager.deleteProfile(profile)
    manager.addProfile(profile)
    manager.setRootProfile(profile.name)
    Disposer.register(project) { manager.deleteProfile(profile.name) }
  }
}

