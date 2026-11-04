// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.projectRoots.impl

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationProvider
import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationService
import com.intellij.openapi.projectRoots.impl.JdkReleaseData
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.common.timeoutRunBlocking

abstract class ExternalJavaConfigurationTest: HeavyPlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  override fun setUp() {
    super.setUp()

    for (versionString in mockJdkVersions) {
      val jdk = IdeaTestUtil.createMockJdk(versionString, "")
      runWriteActionAndWait {
        ProjectJdkTable.getInstance(project).addJdk(jdk)
      }
    }
  }

  abstract val mockJdkVersions: List<String>

  fun <T: JdkReleaseData> checkSuggestion(
    watcher: ExternalJavaConfigurationService,
    configProvider: ExternalJavaConfigurationProvider<T>,
    configFileContent: String,
    expectedVersionString: String,
  ) {
    val file = configProvider.getConfigurationFilePath(project)
    createFile(getOrCreateProjectBaseDir(), file.fileName.toString(), configFileContent)
    val suggestion = timeoutRunBlocking { watcher.getReleaseData(configProvider) }
    assert(suggestion != null)

    val jdk = watcher.findCandidate<T>(suggestion!!, configProvider)
    assert(jdk is ExternalJavaConfigurationService.JdkCandidate.Jdk)
    assertEquals((jdk as ExternalJavaConfigurationService.JdkCandidate.Jdk).jdk.versionString, expectedVersionString)
  }

  override fun tearDown() {
    try {
      runWriteActionAndWait {
        ProjectJdkTable.getInstance(project).apply {
          allJdks.forEach { removeJdk(it) }
        }
      }
    } catch (e: Exception) {
      addSuppressedException(e)
    } finally {
      super.tearDown()
    }
  }
}