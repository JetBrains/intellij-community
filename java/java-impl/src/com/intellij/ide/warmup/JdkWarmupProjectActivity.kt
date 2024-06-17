// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.warmup

import com.intellij.execution.environment.JvmEnvironmentKeyProvider
import com.intellij.ide.environment.EnvironmentService
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.createUniqueSdkName
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry

internal class JdkWarmupProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (Registry.`is`("ide.warmup.use.predicates")) {
      configureJdk(project)
    }
  }

  suspend fun configureJdk(project: Project) {
    if (WarmupStatus.currentStatus() != WarmupStatus.InProgress) {
      return
    }

    val configuredJdk = serviceAsync<EnvironmentService>().getEnvironmentValue(JvmEnvironmentKeyProvider.Keys.JDK_KEY, SENTINEL)
    if (configuredJdk == SENTINEL) {
      println("Environment does not provide configured JDK")
      return
    }

    val jdkName = serviceAsync<EnvironmentService>().getEnvironmentValue(JvmEnvironmentKeyProvider.Keys.JDK_NAME, "warmup_jdk")
    val jdks = ProjectJdkTable.getInstance().allJdks
    val compatibleJdk = jdks.filter { it.homePath == configuredJdk }.run {
      find { it.name == jdkName } ?: firstOrNull()
    }

    val jdk = compatibleJdk ?: JavaSdk.getInstance().createJdk(createUniqueSdkName(jdkName, jdks.toList()), configuredJdk)

    writeAction {
      ProjectRootManager.getInstance(project).projectSdk = jdk
    }
  }
}

private val SENTINEL : String = ""