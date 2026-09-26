// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.openapi.wm.ex.isBackgroundActivitiesSuppressedSync
import com.intellij.openapi.wm.ex.isIndexingActivitiesSuppressedSync
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Path

/**
 * Checks that a welcome-screen project suppresses indexing and background activities (IJPL-253309).
 */
@TestApplication
internal class WelcomeScreenProjectFrameCapabilitiesTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private lateinit var provider: TestWelcomeScreenProjectProvider

  @BeforeEach
  fun setUp() {
    provider = TestWelcomeScreenProjectProvider()
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName<WelcomeScreenProjectProvider>("com.intellij.welcomeScreenProjectProvider"),
      listOf(provider),
      disposable,
    )
  }

  @Test
  fun `welcome project suppresses indexing and background activities`() {
    val project = testProject("WelcomeProject")
    provider.welcomeProject = project

    val capabilities = service<ProjectFrameCapabilitiesService>().getAll(project)
    assertTrue(capabilities.contains(ProjectFrameCapability.WELCOME_EXPERIENCE))
    assertTrue(capabilities.contains(ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES))
    assertTrue(capabilities.contains(ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES))

    assertTrue(isBackgroundActivitiesSuppressedSync(project))
    assertTrue(isIndexingActivitiesSuppressedSync(project))
  }

  @Test
  fun `regular project keeps indexing and background activities`() {
    val project = testProject("RegularProject")

    assertFalse(isBackgroundActivitiesSuppressedSync(project))
    assertFalse(isIndexingActivitiesSuppressedSync(project))
  }

  private class TestWelcomeScreenProjectProvider : WelcomeScreenProjectProvider() {
    var welcomeProject: Project? = null

    override fun canOpenFilesFromSystemFileManager(filePath: Path): Boolean = false

    override fun getWelcomeScreenProjectName(): String = "TestWelcomeProject"

    override fun doIsWelcomeScreenProject(project: Project): Boolean = project === welcomeProject

    override fun doIsForceDisabledFileColors(): Boolean = false

    override fun doGetCreateNewFileProjectPrefix(): String = "testProject"
  }
}

// A proxy project avoids opening a real project and the per-project capability cache.
private fun testProject(name: String): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getName" -> name
      "isDisposed" -> false
      "toString" -> "Project($name)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> null
    }
  }
  return Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java), handler) as Project
}
