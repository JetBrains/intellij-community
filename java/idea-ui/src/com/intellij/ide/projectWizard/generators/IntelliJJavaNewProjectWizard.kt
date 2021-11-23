// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Paths

class IntelliJJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {
  override val name = "IntelliJ"

  override fun createStep(parent: JavaNewProjectWizard.Step) =
    object : IntelliJNewProjectWizardStep<JavaNewProjectWizard.Step>(parent) {
      override fun setupProject(project: Project) {
        val builder = JavaModuleBuilder()
        val moduleFile = Paths.get(moduleFileLocation, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

        builder.name = moduleName
        builder.moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
        builder.contentEntryPath = FileUtil.toSystemDependentName(contentRoot)

        if (parent.context.isCreatingNewProject) {
          // New project with a single module: set project JDK
          parent.context.projectJdk = sdk
        } else {
          // New module in an existing project: set module JDK
          val sameSDK = ProjectRootManager.getInstance(project).projectSdk?.homePath == sdk?.homePath
          builder.moduleJdk = if (sameSDK) null else sdk
        }

        builder.commit(project)
      }
    }
}