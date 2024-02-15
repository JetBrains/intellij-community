// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.jps.model.java.JdkVersionDetector

class JdkVersionUpdaterActivity: ProjectActivity {
  override suspend fun execute(project: Project) {
    ProjectJdkTable.getInstance().allJdks
      .filter { it.sdkType is JavaSdk }
      .forEach { updateJdkVersion(it) }
  }

  private suspend fun updateJdkVersion(jdk: Sdk) {
    val path = jdk.homePath ?: return
    val info = JdkVersionDetector.getInstance().detectJdkVersionInfo(path) ?: return
    val version = info.displayVersionString()

    if (version != jdk.versionString) {
      writeAction {
        jdk.sdkModificator.apply {
          versionString = version
          commitChanges()
        }
      }
    }
  }
}