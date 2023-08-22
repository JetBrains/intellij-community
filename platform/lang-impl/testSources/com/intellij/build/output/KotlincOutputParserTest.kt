// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output

import com.intellij.build.BuildDescriptor
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.NotNull
import org.junit.Test

class KotlincOutputParserTest : LightPlatformTestCase() {

  private lateinit var buildViewTestFixture: BuildViewTestFixture

  override fun setUp() {
    super.setUp()
    buildViewTestFixture = BuildViewTestFixture(project)
    buildViewTestFixture.setUp()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { if (::buildViewTestFixture.isInitialized) buildViewTestFixture.tearDown() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  @Test
  fun `test kotlin error message without file location`() {
    generateBuildWithOutput(
      """
          e: some.BuildException: error text
            at some.App.method(App.kt:5)
            at some.Util.method(Util.kt:10)
            
      """.trimIndent()
    )
    with(buildViewTestFixture) {
      assertBuildViewTreeEquals(
        """
        -
         -finished
          some.BuildException: error text
        """.trimIndent()
      )
      assertBuildViewSelectedNode(
        "some.BuildException: error text",
        """
          some.BuildException: error text
            at some.App.method(App.kt:5)
            at some.Util.method(Util.kt:10)

        """.trimIndent()
      )
    }
  }

  @Test
  fun `test kotlin error message with uri file location`() {
    generateBuildWithOutput(
      """
          e: file://C:/A.kt: (7, 5): Unresolved reference: bbb
                      
      """.trimIndent()
    )
    with(buildViewTestFixture) {
      assertBuildViewTreeEquals(
        """
          -
           -finished
            file://C:/A.kt: (7, 5): Unresolved reference: bbb
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test kotlin error message with file location`() {
    generateBuildWithOutput(
      """
          e: C:\A.kt: (7, 5): Unresolved reference: bbb
            
      """.trimIndent()
    )
    with(buildViewTestFixture) {
      assertBuildViewTreeEquals(
        """
          -
           -finished
            C:\A.kt: (7, 5): Unresolved reference: bbb
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test different file paths are parsed`() {
    val paths = listOf("e: file:///C:/JB/tasks/KTIJ-22428/untitled/src/main/kotlin/A.kt:7:5 Unresolved reference: bbb\n",
      "e: C:\\A.kt: (7, 5): Unresolved reference: bbb\n",
      "e: file:////wsl$/Ubuntu/home/A.kt:7:5 Unresolved reference: bbb\n",
      "e: \\\\wsl\$\\Ubuntu\\home\\A.kt: (7, 5): Unresolved reference: bbb\n"
    )
    for (line in paths) {
      assert(line.contains(KotlincOutputParser.Companion.extractPath(line) ?: "path not found")) {
        "Failed to find path in [$line]"
      }
    }

  }

  private fun generateBuildWithOutput(output: String) {
    BuildViewManager.createBuildProgress(project)
      .start(progressDescriptor())
      .apply { fireOutputEvents(output) }
      .finish()
  }

  private fun @NotNull BuildProgress<BuildProgressDescriptor>.fireOutputEvents(output: String) {
    BuildOutputInstantReaderImpl(id, id, project.service<BuildViewManager>(), listOf(KotlincOutputParser()))
      .append(output)
      .closeAndGetFuture()
      .get()
  }

  private fun progressDescriptor(): BuildProgressDescriptor {
    val buildDescriptor = DefaultBuildDescriptor(Object(), "A Build", "", System.currentTimeMillis())
    return object : BuildProgressDescriptor {
      override fun getBuildDescriptor(): BuildDescriptor = buildDescriptor
      override fun getTitle(): String = buildDescriptor.title
    }
  }
}
