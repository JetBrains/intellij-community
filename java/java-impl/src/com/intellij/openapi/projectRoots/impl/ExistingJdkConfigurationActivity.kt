// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.writeAction
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
class ExistingJdkConfigurationActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!Registry.`is`("jdk.configure.existing", false)) return

    val javaSdk = JavaSdk.getInstance()
    val registeredJdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk)
    val jdkPathsToAdd = ArrayList<String>()

    // Collect JDKs to register
    JdkFinder.getInstance().suggestHomePaths().forEach { homePath: String ->
      if (javaSdk.isValidSdkHome(homePath) && registeredJdks.none {
          FileUtil.toCanonicalPath(it.homePath) == FileUtil.toCanonicalPath(homePath)
        }) {
        jdkPathsToAdd.add(homePath)
      }
    }

    val rootManager = ProjectRootManager.getInstance(project)
    val addedJdks = registeredJdks.toMutableList()
    writeAction {
      // Register collected JDKs
      for (path in jdkPathsToAdd) {
        addedJdks.add(SdkConfigurationUtil.createAndAddSDK(path, javaSdk))
      }

      // Set project SDK
      if (rootManager.projectSdk == null) {
        addedJdks
          .filterNotNull()
          .maxByOrNull { JavaSdk.getInstance().getVersion(it)?.ordinal ?: 0 }
          ?.let { rootManager.projectSdk = it }
      }
    }
  }
}
