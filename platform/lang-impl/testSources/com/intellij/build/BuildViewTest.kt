// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build

import com.intellij.build.events.BuildEventPresentationData
import com.intellij.build.events.MessageEvent.Kind.*
import com.intellij.build.events.PresentableBuildEvent
import com.intellij.build.events.impl.*
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.Platform
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.SystemProperties
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.tree.TreeUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.util.function.Function
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

class BuildViewTest : LightPlatformTestCase() {

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
  fun `test successful build`() {
    val title = "A build"
    val buildDescriptor = DefaultBuildDescriptor(Object(), title, "", System.currentTimeMillis())
    val progressDescriptor = object : BuildProgressDescriptor {
      override fun getBuildDescriptor(): BuildDescriptor = buildDescriptor
      override fun getTitle(): String = title
    }

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(progressDescriptor)
        .message("Root message", "Tex of the root message console", INFO, null)
        .progress("Running…")
        .startChildProgress("Inner progress")
          .fileMessage("File message1", "message1 descriptive text", INFO, FilePosition(File("aFile.java"), 0, 0))
          .fileMessage("File message2", "message2 descriptive text", INFO, FilePosition(File("aFile.java"), 0, 0))
        .finish()
      .finish()
    // @formatter:on

    buildViewTestFixture.assertBuildViewTreeEquals(
      """
      -
       -finished
        Root message
        -Inner progress
         -aFile.java
          File message1
          File message2
      """.trimIndent()
    )

    buildViewTestFixture.assertBuildViewNode("finished", "")
    buildViewTestFixture.assertBuildViewNode("Root message", "Tex of the root message console\n")
    buildViewTestFixture.assertBuildViewNode(
      "File message1",
      "aFile.java\n" +
      "message1 descriptive text"
    )
  }

  @Test
  fun `test file messages presentation`() {
    val title = "A build"
    val tempDirectory = FileUtil.getTempDirectory() + "/project"
    val buildDescriptor = DefaultBuildDescriptor(Object(), title, tempDirectory, System.currentTimeMillis())
    val progressDescriptor = object : BuildProgressDescriptor {
      override fun getBuildDescriptor(): BuildDescriptor = buildDescriptor
      override fun getTitle(): String = title
    }

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(progressDescriptor)
      .fileMessage("message 1", "message 1 descriptive text", INFO, FilePosition(File("aFile1.java"), 0, 0))
      .fileMessage("message 1.1", "message 1.1 descriptive text", WARNING, FilePosition(File("aFile1.java"), 0, 0))

      .fileMessage("message 2", "message 2 descriptive text", WARNING, FilePosition(File(tempDirectory, "project/aFile2.java"), 0, 0))
      .fileMessage("message 2.1", "message 2.1 descriptive text", WARNING, FilePosition(File(tempDirectory), -1, -1))

      .fileMessage("message 3", "message 3 descriptive text", WARNING, FilePosition(File(tempDirectory, "anotherDir1/aFile3.java"), 0, 0))
      .fileMessage("message 3.1", "message 3.1 descriptive text", ERROR, FilePosition(File(tempDirectory, "anotherDir2/aFile3.java"), 0, 0))

      .fileMessage("message 4", "message 4 descriptive text", INFO, FilePosition(File(SystemProperties.getUserHome(), "foo/aFile4.java"), 0, 0))
      .finish()
    // @formatter:on

    buildViewTestFixture.assertBuildViewTreeEquals(
      """
      -
       -finished
        -aFile1.java
         message 1
         message 1.1
        -aFile2.java
         message 2
        message 2.1
        -aFile3.java
         message 3
        -aFile3.java
         message 3.1
        -aFile4.java
         message 4""".trimIndent()
    )

    val buildView = project.service<BuildViewManager>().getBuildView(buildDescriptor.id)
    val buildTreeConsoleView = buildView!!.getView(BuildTreeConsoleView::class.java.name, BuildTreeConsoleView::class.java)
    val visitor = runInEdtAndGet {
      val tree = buildTreeConsoleView!!.tree
      return@runInEdtAndGet CollectingTreeVisitor().also {
        TreeUtil.visitVisibleRows(tree, it)
      }
    }
    assertThat(visitor.userObjects)
      .extracting(Function<Any?, String?> { node ->
        val presentation = (node as ExecutionNode).presentation
        if (presentation.coloredText.isEmpty()) {
          presentation.presentableText
        }
        else {
          presentation.coloredText.joinToString(separator = " =>") { it.text }
        }
      })
      .containsOnlyOnce(
        "aFile1.java =>  1 warning",
        "message 1 => :1",
        "message 1.1 => :1",
        "aFile2.java => project 1 warning",
        "message 2 => :1",
        "message 2.1",
        "aFile3.java => anotherDir1 1 warning",
        "message 3 => :1",
        "aFile3.java => anotherDir2 1 error",
        "message 3.1 => :1",
        "aFile4.java => ~${Platform.current().fileSeparator}foo",
        "message 4 => :1"
      )
  }

  @Test
  fun `test build with errors`() {
    val title = "A build"
    val buildDescriptor = DefaultBuildDescriptor(Object(), title, "", System.currentTimeMillis())
    val progressDescriptor = object : BuildProgressDescriptor {
      override fun getBuildDescriptor(): BuildDescriptor = buildDescriptor
      override fun getTitle(): String = title
    }

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(progressDescriptor)
        .message("Root message", "Tex of the root message console", INFO, null)
        .progress("Running…")
        .startChildProgress("Inner progress")
          .fileMessage("File message1", "message1 descriptive text", ERROR, FilePosition(File("aFile.java"), 0, 0))
          .fileMessage("File message2", "message2 descriptive text", ERROR, FilePosition(File("aFile.java"), 0, 0))
        .fail()
      .fail()
    // @formatter:on

    buildViewTestFixture.assertBuildViewTreeEquals(
      """
      -
       -failed
        Root message
        -Inner progress
         -aFile.java
          File message1
          File message2
      """.trimIndent()
    )

    buildViewTestFixture.assertBuildViewSelectedNode(
      "File message1",
      "aFile.java\n" +
      "message1 descriptive text"
    )
    buildViewTestFixture.assertBuildViewNode("failed", "")
    buildViewTestFixture.assertBuildViewNode("Root message", "Tex of the root message console\n")
  }

  @Test
  fun `test cancelled build`() {
    val title = "A build"
    val buildDescriptor = DefaultBuildDescriptor(Object(), title, "", System.currentTimeMillis())
    val progressDescriptor = object : BuildProgressDescriptor {
      override fun getBuildDescriptor(): BuildDescriptor = buildDescriptor
      override fun getTitle(): String = title
    }

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(progressDescriptor)
        .message("Root message", "Tex of the root message console", INFO, null)
        .progress("Running…")
        .startChildProgress("Inner progress")
        .cancel()
      .cancel()
    // @formatter:on

    buildViewTestFixture.assertBuildViewTreeEquals(
      """
      -
       -cancelled
        Root message
        Inner progress
      """.trimIndent()
    )

    buildViewTestFixture.assertBuildViewNode("cancelled", "")
    buildViewTestFixture.assertBuildViewNode("Root message", "Tex of the root message console\n")
    buildViewTestFixture.assertBuildViewNodeConsole("Inner progress", ::assertNull)
  }

  @Test
  fun `test build view listeners`() {
    val title = "A build"
    val buildDescriptor = DefaultBuildDescriptor(Object(), title, "", System.currentTimeMillis())
    val progressDescriptor = object : BuildProgressDescriptor {
      override fun getBuildDescriptor(): BuildDescriptor = buildDescriptor
      override fun getTitle(): String = title
    }

    val buildMessages = mutableListOf<String>()
    //BuildViewManager
    project.service<BuildViewManager>().addListener(
      BuildProgressListener { _, event -> buildMessages.add(event.message) },
      testRootDisposable
    )

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(progressDescriptor)
        .output("Build greeting\n", true)
        .message("Root message", "Text of the root message console", INFO, null)
        .progress("Running…")
        .startChildProgress("Inner progress")
          .output("inner progress output", true)
          .fileMessage("File message1", "message1 descriptive text", INFO, FilePosition(File("aFile.java"), 0, 0))
          .fileMessage("File message2", "message2 descriptive text", INFO, FilePosition(File("aFile.java"), 0, 0))
        .finish()
      .output("Build farewell", true)
      .finish()
    // @formatter:on

    buildViewTestFixture.assertBuildViewTreeEquals(
      """
      -
       -finished
        Root message
        -Inner progress
         -aFile.java
          File message1
          File message2
      """.trimIndent()
    )

    buildViewTestFixture.assertBuildViewNode("finished", "Build greeting\n" +
                                                         "Build farewell")
    buildViewTestFixture.assertBuildViewNode("Inner progress", "inner progress output")
    buildViewTestFixture.assertBuildViewNode("Root message", "Text of the root message console\n")
    buildViewTestFixture.assertBuildViewNode("File message1", "aFile.java\n" +
                                                              "message1 descriptive text")

    assertEquals("running…" +
                 "Build greeting\n" +
                 "Root message" +
                 "Running…" +
                 "Inner progress" +
                 "inner progress output" +
                 "File message1" +
                 "File message2" +
                 "Inner progress" +
                 "Build farewell" +
                 "finished", buildMessages.joinToString(""))
  }

  @Test
  fun `test presentable build event`() {
    val title = "A build"
    val buildId = Object()
    val buildDescriptor = DefaultBuildDescriptor(buildId, title, "", System.currentTimeMillis())

    val buildViewManager = project.service<BuildViewManager>()
    buildViewManager.onEvent(buildId, StartBuildEventImpl(buildDescriptor, "started"))

    val component = JButton("test button")
    class MyPresentableBuildEvent(eventId: Any, parentId: Any?, eventTime: Long, message: String) :
      AbstractBuildEvent(eventId, parentId, eventTime, message), PresentableBuildEvent {
      override fun getPresentationData(): BuildEventPresentationData {
        return object : BuildEventPresentationData {
          override fun getNodeIcon(): Icon = AllIcons.General.Add
          override fun getExecutionConsole(): ExecutionConsole {
            return object : ExecutionConsole {
              override fun getComponent(): JComponent = component
              override fun getPreferredFocusableComponent(): JComponent = component
              override fun dispose() {}
            }
          }

          override fun consoleToolbarActions(): ActionGroup? = null
        }
      }
    }

    buildViewManager.onEvent(buildId, MyPresentableBuildEvent("1", buildId, System.currentTimeMillis(), "my event"))
    buildViewManager.onEvent(buildId,
                             ProgressBuildEventImpl("1", buildId, System.currentTimeMillis(), "my event node text updated", -1, -1, ""))
    buildViewManager.onEvent(buildId, FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "finished", SuccessResultImpl()))

    buildViewTestFixture.assertBuildViewTreeEquals(
      """
      -
       -finished
        my event node text updated
      """.trimIndent()
    )

    buildViewTestFixture.assertBuildViewNodeConsole("my event node text updated") { executionConsole ->
      assertThat(executionConsole!!.component)
        .isEqualTo(component)
        .matches { (it as JButton).text == "test button" }
    }
  }
}
