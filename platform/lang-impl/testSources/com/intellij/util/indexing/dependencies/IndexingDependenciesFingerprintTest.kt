// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.application
import com.intellij.util.indexing.dependencies.IndexingDependenciesFingerprint.Companion.FINGERPRINT_SIZE_IN_BYTES
import com.intellij.util.indexing.dependencies.IndexingDependenciesFingerprint.Companion.NULL_FINGERPRINT
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class IndexingDependenciesFingerprintTest {

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @Rule
  @JvmField
  val temp = TempDirectory()

  private val testDisposable = Disposer.newDisposable()
  private lateinit var factory: TestFactories

  @Before
  fun setup() {
    val tmpDir = temp.newDirectory("fileIndexingStamp")
    factory = TestFactories(tmpDir, testDisposable, useApplication = true)
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun `remind to change storage version when fingerprint size changes`() {
    assertEquals("If you change fingerprint size, you should also change storage version",
                 32, FINGERPRINT_SIZE_IN_BYTES)
  }

  @Test
  fun `test NULL fingerprint size`() {
    assertEquals(FINGERPRINT_SIZE_IN_BYTES, NULL_FINGERPRINT.toByteBuffer().remaining())
  }

  @Test
  fun `test not-NULL fingerprint size`() {
    val fingerprintService = application.service<IndexingDependenciesFingerprint>()
    assertEquals(FINGERPRINT_SIZE_IN_BYTES, fingerprintService.getFingerprint().toByteBuffer().remaining())
  }

  @Test
  fun `test project indexing request token changes after fingerprint change`() {
    val projectDependencies = factory.newProjectIndexingDependenciesService()
    val fingerprintService = application.service<IndexingDependenciesFingerprint>()

    val tokenBefore = projectDependencies.getLatestIndexingRequestToken()
    fingerprintService.changeFingerprintInTest()
    val tokenAfter = projectDependencies.getLatestIndexingRequestToken()
    assertNotEquals(tokenBefore, tokenAfter)
  }

  @Test
  fun `test fingerprint stored after restart`() {
    val appStorage = factory.nonExistingFile("app")
    val projectStorage = factory.nonExistingFile("project")
    val appDependencies1 = factory.newAppIndexingDependenciesService(appStorage)
    val projectDependencies1 = factory.newProjectIndexingDependenciesService(projectStorage, appDependencies1)

    val fingerprintService = application.service<IndexingDependenciesFingerprint>()
    fingerprintService.changeFingerprintInTest()

    val tokenBefore = projectDependencies1.getLatestIndexingRequestToken()

    Disposer.dispose(projectDependencies1)
    Disposer.dispose(appDependencies1)

    val appDependencies2 = factory.newAppIndexingDependenciesService(appStorage)
    val projectDependencies2 = factory.newProjectIndexingDependenciesService(projectStorage, appDependencies2)
    val tokenAfter = projectDependencies2.getLatestIndexingRequestToken()

    assertEquals(tokenBefore, tokenAfter)
  }
}