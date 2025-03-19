// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.VfsTestUtil.createFile
import kotlinx.coroutines.runBlocking

abstract class ExternalJavaConfigurationTest: HeavyPlatformTestCase() {
  override fun setUp() {
    super.setUp()

    for (versionString in mockJdkVersions) {
      val jdk = IdeaTestUtil.createMockJdk(versionString, "")
      SdkConfigurationUtil.addSdk(jdk)
    }
  }

  abstract val mockJdkVersions: List<String>

  fun <T> checkSuggestion(
    watcher: ExternalJavaConfigurationService,
    configProvider: ExternalJavaConfigurationProvider<T>,
    configFileContent: String,
    expectedVersionString: String,
  ) {
    val file = configProvider.getConfigurationFile(project)
    createFile(getOrCreateProjectBaseDir(), file.name, configFileContent)
    val suggestion = runBlocking { watcher.getReleaseData(configProvider) }
    assert(suggestion != null)

    val jdk = watcher.findCandidate<T>(suggestion!!, configProvider)
    assert(jdk is ExternalJavaConfigurationService.JdkCandidate.Jdk)
    assertEquals((jdk as ExternalJavaConfigurationService.JdkCandidate.Jdk).jdk.versionString, expectedVersionString)
  }

  override fun tearDown() {
    try {
      runWriteActionAndWait {
        ProjectJdkTable.getInstance().apply {
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