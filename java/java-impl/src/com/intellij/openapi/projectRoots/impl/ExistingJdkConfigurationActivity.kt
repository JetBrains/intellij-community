// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JdkFinder
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JdkVersionDetector

/**
 * This [ProjectActivity] makes it possible to index an installed JDK without any
 * configuration.
 * The registry key `jdk.configure.existing` must be enabled.
 *
 *  - Automatically registers JDKs on the computer in the [ProjectJdkTable]
 *  - Uses the first JDK found as project SDK if none was configured
 */
internal class ExistingJdkConfigurationActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!Registry.`is`("jdk.configure.existing", false)) {
      return
    }

    // Check if the project JDK is already set
    val rootManager = project.serviceAsync<ProjectRootManager>()
    if (rootManager.projectSdk != null) return

    val javaHomePaths = JavaHomeFinder.getFinder(project).findInJavaHome()

    // Set the project JDK from the JDK table
    val registeredJdks = serviceAsync<ProjectJdkTable>().getSdksOfType(JavaSdk.getInstance())
    if (!registeredJdks.isEmpty()) {
      val jdk = registeredJdks.firstOrNull { it.homePath in javaHomePaths } ?:
                registeredJdks.maxByOrNull { JavaSdk.getInstance().getVersion(it)?.ordinal ?: 0 }
      jdk?.let { rootManager.projectSdk = it }
      return
    }

    // Set the project JDK from detected paths
    val detectedPaths = serviceAsync<JdkFinder>().suggestHomePaths(project)
    val paths = detectedPaths.filter { it in javaHomePaths }.ifEmpty { detectedPaths }

    val jdkPathToSetup = withContext(Dispatchers.IO) {
      paths
        .map { it to JdkVersionDetector.getInstance().detectJdkVersionInfo(it) }
        .maxByOrNull { it.second?.version?.feature ?: 0 }
        ?.first
    } ?: return

    edtWriteAction {
      rootManager.projectSdk = service<AddJdkService>().createIncompleteJdk(jdkPathToSetup)
    }
  }
}
