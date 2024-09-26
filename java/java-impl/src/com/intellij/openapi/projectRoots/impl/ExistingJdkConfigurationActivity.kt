// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JdkFinder
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry

/**
 * This [ProjectActivity] makes it possible to index an installed JDK without any
 * configuration.
 * The registry key `jdk.configure.existing` must be enabled.
 *
 *  - Automatically registers JDKs on the computer in the [ProjectJdkTable]
 *  - Uses the first JDK found as project SDK if none was configured
 */
private class ExistingJdkConfigurationActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!Registry.`is`("jdk.configure.existing", false)) {
      return
    }

    val javaSdk = JavaSdk.getInstance()
    val registeredJdks = serviceAsync<ProjectJdkTable>().getSdksOfType(javaSdk)
    val jdkPathsToAdd = ArrayList<String>()

    // Collect JDKs to register
    serviceAsync<JdkFinder>().suggestHomePaths().forEach { homePath: String ->
      if (javaSdk.isValidSdkHome(homePath) && registeredJdks.none {
          FileUtil.toCanonicalPath(it.homePath) == FileUtil.toCanonicalPath(homePath)
        }) {
        jdkPathsToAdd.add(homePath)
      }
    }

    val rootManager = project.serviceAsync<ProjectRootManager>()
    val addedJdks = registeredJdks.toMutableList()

    val priorityPaths = JavaHomeFinder.getFinder(project).findInJavaHome()

    writeAction {
      // Register collected JDKs
      for (path in jdkPathsToAdd) {
        addedJdks.add(SdkConfigurationUtil.createAndAddSDK(path, javaSdk))
      }

      // Set project SDK
      if (rootManager.projectSdk == null) {
        val jdk = addedJdks.firstOrNull { it.homePath in priorityPaths } ?:
                  addedJdks.maxByOrNull { JavaSdk.getInstance().getVersion(it)?.ordinal ?: 0 }
        jdk?.let { rootManager.projectSdk = it }
      }
    }
  }
}
