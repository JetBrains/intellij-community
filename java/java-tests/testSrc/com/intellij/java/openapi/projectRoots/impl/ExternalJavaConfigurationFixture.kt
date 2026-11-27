// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.projectRoots.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationProvider
import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationService
import com.intellij.openapi.projectRoots.impl.JdkReleaseData
import com.intellij.testFramework.HeavyTestHelper
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * A new opened project for each test, with a mock JDK in the JDK table for each version string of the fixture.
 * The fixture removes all JDKs from the JDK table after the test.
 */
@TestOnly
class ExternalJavaConfigurationFixture(val project: Project) {
  fun <T : JdkReleaseData> checkSuggestion(
    watcher: ExternalJavaConfigurationService,
    configProvider: ExternalJavaConfigurationProvider<T>,
    configFileContent: String,
    expectedVersionString: String,
  ) {
    val file = configProvider.getConfigurationFilePath(project)
    createFile(HeavyTestHelper.getOrCreateProjectBaseDir(project), file.fileName.toString(), configFileContent)
    val suggestion = timeoutRunBlocking { watcher.getReleaseData(configProvider) }
    assert(suggestion != null)

    val jdk = watcher.findCandidate<T>(suggestion!!, configProvider)
    assert(jdk is ExternalJavaConfigurationService.JdkCandidate.Jdk)
    assertEquals((jdk as ExternalJavaConfigurationService.JdkCandidate.Jdk).jdk.versionString, expectedVersionString)
  }
}

/**
 * Creates an [ExternalJavaConfigurationFixture] with a mock JDK for each of [mockJdkVersions]. Declare it as an instance field.
 */
@TestOnly
fun externalJavaConfigurationFixture(mockJdkVersions: List<String>): TestFixture<ExternalJavaConfigurationFixture> = testFixture {
  val project = projectFixture(openAfterCreation = true).init()
  for (versionString in mockJdkVersions) {
    val jdk = IdeaTestUtil.createMockJdk(versionString, "")
    edtWriteAction {
      ProjectJdkTable.getInstance(project).addJdk(jdk)
    }
  }
  initialized(ExternalJavaConfigurationFixture(project)) {
    edtWriteAction {
      ProjectJdkTable.getInstance(project).apply {
        allJdks.forEach { removeJdk(it) }
      }
    }
  }
}
