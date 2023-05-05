// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.warmup

import com.intellij.execution.environment.JvmEnvironmentKeyProvider
import com.intellij.ide.environment.EnvironmentService
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import java.nio.file.Path

class JdkWarmupConfigurator : WarmupConfigurator {

  override val configuratorPresentableName: String = "warmupJdkConfigurator"

  override suspend fun prepareEnvironment(projectPath: Path) {
    val configuredJdk = service<EnvironmentService>().getValue(JvmEnvironmentKeyProvider.JDK_KEY, null)
    if (configuredJdk == null) {
      println("Environment does not provide configured JDK")
      return
    }
    val configuredJdkPath = Path.of(configuredJdk)
    val jdkName = service<EnvironmentService>().getValue(JvmEnvironmentKeyProvider.JDK_NAME, "warmup_jdk")!!
    val jdk = JavaSdk.getInstance().createJdk(jdkName, configuredJdkPath.toString())
    writeAction {
      ProjectJdkTable.getInstance().addJdk(jdk)
    }
    val defaultProject = ProjectManager.getInstance().defaultProject
    writeAction {
      ProjectRootManager.getInstance(defaultProject).projectSdk = jdk
    }
  }

  override suspend fun runWarmup(project: Project): Boolean {
    return false
  }
}
