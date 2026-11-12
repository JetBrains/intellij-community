// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelType
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass

/**
 * End-to-end coverage of [BuildManager.getBuildProcessRuntimeSdk] in local and Docker environments.
 *
 * Requires the `intellij.idea.ultimate.tests.main` classpath — `BuildManager` needs the Java/Compiler
 * plugins and the Docker scenario needs the ultimate-only `intellij.platform.ijent.testFramework` SPI:
 *
 * ```
 * ./tests.cmd --module intellij.java.compiler.tests.main \
 *             --test com.intellij.compiler.server.BuildManagerSdkSelectionTest
 * ```
 * (which internally switches the run context to `intellij.idea.ultimate.tests.main`).
 *
 * To run from IDEA: edit the JUnit run configuration's "Use classpath of module" to `intellij.idea.ultimate.tests.main`.
 *
 * The class is multiplexed across potentially whatever EEL machines the framework can construct.
 * The Docker scenario pins an Eclipse Temurin 8 image so the "JDK is too old" branch fires deterministically;
 * the local scenario exercises the same logic with the IDE's internal JDK, where the throw should never happen.
 */
@Disabled("IJPL-246357 — the JUnit 5 close handler in this class surfaces a pre-existing Light-project leak from JUnit 3 tests in the same JVM. Re-enable when the underlying plugin-side Disposer registration is fixed.")
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@DockerTest(image = TEMURIN_8_IMAGE, mandatory = false)
@ParameterizedClass
class BuildManagerSdkSelectionTest(private val eelHolder: EelHolder) {

  private val project: Project by projectFixture(openAfterCreation = true)

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `getBuildProcessRuntimeSdk throws found-too-old when the only available JDK is JDK 8`() {
    // This scenario is the IJPL-240092 reproducer: a non-local project with only a JDK that
    // is too old for the JPS build process. Only meaningful when running against a Docker EEL
    // whose image has JDK 8 pre-installed.
    assumeTrue(eelHolder.type == EelType.Docker, "Only runs against the Docker Temurin-8 image")

    val sdk = installJdkOnEel(name = "temurin-8", javaHome = TEMURIN_8_HOME, versionString = "1.8.0_492")
    val expectedJdkLabel = "${sdk.name} (${sdk.versionString})"
    val envName = EelProviderUtil.getEelDescriptor(project).name

    val ex = assertThrows<IllegalStateException> {
      BuildManager.getBuildProcessRuntimeSdk(project)
    }
    val expected = JavaCompilerBundle.message(
      "build.manager.launch.build.process.failed.to.find.compatible.jdk.found.too.old",
      expectedJdkLabel, envName, JPS_MIN_FEATURE
    )
    assertThat(ex.message).isEqualTo(expected)
  }

  @Test
  fun `getBuildProcessRuntimeSdk falls back to internal JDK on a local project`() {
    // The other half of the matrix: when the project is local, the IDE's internal JDK is used
    // as the fallback and no exception is thrown — even if the user has no compatible JDK in
    // their SDK table.
    assumeTrue(eelHolder.type == EelType.Local, "Only meaningful on the local EEL scenario")

    val result = BuildManager.getBuildProcessRuntimeSdk(project)
    assertThat(result.first).isNotNull()
  }

  @Suppress("SameParameterValue")
  private fun installJdkOnEel(name: String, javaHome: String, versionString: String): Sdk {
    val eelPath = EelPath.parse(javaHome, eelHolder.eel.descriptor).asNioPath()
    return WriteAction.computeAndWait<Sdk, Throwable> {
      val table = ProjectJdkTable.getInstance()
      val sdk = table.createSdk(name, JavaSdk.getInstance())
      sdk.sdkModificator.apply {
        this.homePath = eelPath.toString()
        this.versionString = versionString
        commitChanges()
      }
      table.addJdk(sdk, disposable)
      ProjectRootManager.getInstance(project).projectSdk = sdk
      sdk
    }
  }

  companion object {
    private const val JPS_MIN_FEATURE: Int = 11
  }
}

// Top-level so the `@DockerTest(image = ...)` annotation can read them at compile time.
// The fully-qualified `docker.io/library/...` form deliberately bypasses the test framework's
// auto-wrap to the JetBrains Space mirror (which requires authentication) — the image is
// pulled straight from Docker Hub.
internal const val TEMURIN_8_IMAGE: String = "docker.io/library/eclipse-temurin:8-jdk"
internal const val TEMURIN_8_HOME: String = "/opt/java/openjdk"
