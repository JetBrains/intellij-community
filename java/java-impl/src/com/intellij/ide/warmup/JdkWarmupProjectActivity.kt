// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.warmup

import com.intellij.execution.environment.JvmEnvironmentKeyProvider
import com.intellij.ide.environment.EnvironmentService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import java.nio.file.Path

class JdkWarmupProjectActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    if (!Registry.`is`("ide.warmup.use.predicates")) {
      return
    }
    configureJdk(project)
  }

  suspend fun configureJdk(project: Project) {
    if (WarmupStatus.currentStatus(ApplicationManager.getApplication()) != WarmupStatus.InProgress) {
      return
    }
    val configuredJdk = serviceAsync<EnvironmentService>().getEnvironmentValue(JvmEnvironmentKeyProvider.Keys.JDK_KEY, SENTINEL)
    if (configuredJdk == SENTINEL) {
      println("Environment does not provide configured JDK")
      return
    }
    val configuredJdkPath = Path.of(configuredJdk)
    val jdkName = serviceAsync<EnvironmentService>().getEnvironmentValue(JvmEnvironmentKeyProvider.Keys.JDK_NAME, "warmup_jdk")
    val jdk = JavaSdk.getInstance().createJdk(jdkName, configuredJdkPath.toString())
    writeAction {
      ProjectJdkTable.getInstance().addJdk(jdk)
    }
    writeAction {
      ProjectRootManager.getInstance(project).projectSdk = jdk
    }
  }
}

private val SENTINEL : String = ""