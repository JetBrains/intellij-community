// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.build.BuildTreeConsoleViewTest.Companion.assertTitleElements
import com.intellij.build.events.BuildEventPresentationData
import com.intellij.build.events.MessageEvent.Kind.ERROR
import com.intellij.build.events.MessageEvent.Kind.INFO
import com.intellij.build.events.MessageEvent.Kind.WARNING
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.progress.BuildProgressDescriptorImpl
import com.intellij.execution.Platform
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewTree
import com.intellij.platform.testFramework.assertion.assertConsoleText
import com.intellij.platform.testFramework.assertion.consoleView
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

@TestApplication
class BuildViewTest {

  private val projectFixture = projectFixture()
  private val project by projectFixture

  private val buildViewTestFixture by testFixture {
    val project = projectFixture.init()
    val fixture = BuildViewTestFixture(project)
    fixture.setUp()
    initialized(fixture) {
      fixture.tearDown()
    }
  }

  private val buildView get() = buildViewTestFixture.buildView

  @Test
  fun `test successful build`() {
    val buildDescriptor = DefaultBuildDescriptor(Any(), "A build", "", System.currentTimeMillis())

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(BuildProgressDescriptorImpl(buildDescriptor))
        .message("Root message", "Text of the root message console", INFO, null)
      .progress("Running…")
        .startChildProgress("Inner progress")
          .fileMessage("File message1", "message1 descriptive text", INFO, FilePosition(Path.of("aFile.java"), 0, 0))
          .fileMessage("File message2", "message2 descriptive text", INFO, FilePosition(Path.of("aFile.java"), 0, 0))
        .finish()
      .finish()
    // @formatter:on

    assertBuildViewTree(buildView) {
      assertNode("finished") {
        assertConsoleText("")
        assertNode("Root message") {
          assertConsoleText("Text of the root message console\n")
        }
        assertNode("Inner progress") {
          assertNode("aFile.java") {
            assertNode("File message1") {
              assertConsoleText("message1 descriptive text")
            }
            assertNode("File message2") {
              assertConsoleText("message2 descriptive text")
            }
          }
        }
      }
    }
  }

  @Test
  fun `test file messages presentation`() {
    val tempDirectory = FileUtil.getTempDirectory() + "/project"
    val buildDescriptor = DefaultBuildDescriptor(Any(), "A build", tempDirectory, System.currentTimeMillis())

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(BuildProgressDescriptorImpl(buildDescriptor))
        .fileMessage("message 1", "message 1 descriptive text", INFO, FilePosition(Path.of("aFile1.java"), 0, 0))
        .fileMessage("message 1.1", "message 1.1 descriptive text", WARNING, FilePosition(Path.of("aFile1.java"), 0, 0))
        .fileMessage("message 2", "message 2 descriptive text", WARNING, FilePosition(Path.of(tempDirectory, "project/aFile2.java"), 0, 0))
        .fileMessage("message 2.1", "message 2.1 descriptive text", WARNING, FilePosition(Path.of(tempDirectory), -1, -1))
        .fileMessage("message 3", "message 3 descriptive text", WARNING, FilePosition(Path.of(tempDirectory, "anotherDir1/aFile3.java"), 0, 0))
        .fileMessage("message 3.1", "message 3.1 descriptive text", ERROR, FilePosition(Path.of(tempDirectory, "anotherDir2/aFile3.java"), 0, 0))
        .fileMessage("message 4", "message 4 descriptive text", INFO, FilePosition(Path.of(SystemProperties.getUserHome(), "foo/aFile4.java"), 0, 0))
      .finish()
    // @formatter:on

    assertBuildViewTree(buildView.eventView!!) {
      assertNode("finished") {
        assertNode("aFile1.java") {
          assertTitleElements("aFile1.java", "  1 warning")
          assertNode("message 1") {
            assertTitleElements("message 1", " :1")
          }
          assertNode("message 1.1") {
            assertTitleElements("message 1.1", " :1")
          }
        }
        assertNode("aFile2.java") {
          assertTitleElements("aFile2.java", " project 1 warning")
          assertNode("message 2") {
            assertTitleElements("message 2", " :1")
          }
        }
        assertNode("message 2.1") {
          assertTitleElements("message 2.1")
        }
        assertNode("aFile3.java") {
          assertTitleElements("aFile3.java", " anotherDir1 1 warning")
          assertNode("message 3") {
            assertTitleElements("message 3", " :1")
          }
        }
        assertNode("aFile3.java") {
          assertTitleElements("aFile3.java", " anotherDir2 1 error")
          assertNode("message 3.1") {
            assertTitleElements("message 3.1", " :1")
          }
        }
        assertNode("aFile4.java") {
          assertTitleElements("aFile4.java", " ~${Platform.current().fileSeparator}foo")
          assertNode("message 4") {
            assertTitleElements("message 4", " :1")
          }
        }
      }
    }
  }

  @Test
  fun `test build with errors`() {
    val buildDescriptor = DefaultBuildDescriptor(Any(), "A build", "", System.currentTimeMillis())

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(BuildProgressDescriptorImpl(buildDescriptor))
        .message("Root message", "Text of the root message console", INFO, null)
      .progress("Running…")
        .startChildProgress("Inner progress")
          .fileMessage("File message1", "message1 descriptive text", ERROR, FilePosition(Path.of("aFile.java"), 0, 0))
          .fileMessage("File message2", "message2 descriptive text", ERROR, FilePosition(Path.of("aFile.java"), 0, 0))
        .fail()
      .fail()
    // @formatter:on

    assertBuildViewTree(buildView) {
      assertNode("failed") {
        assertConsoleText("")
        assertNode("Root message") {
          assertConsoleText("Text of the root message console\n")
        }
        assertNode("Inner progress") {
          assertNode("aFile.java") {
            assertNode("File message1") {
              assertConsoleText("message1 descriptive text")
            }
            assertNode("File message2") {
              assertConsoleText("message2 descriptive text")
            }
          }
        }
      }
    }
  }

  @Test
  fun `test cancelled build`() {
    val buildDescriptor = DefaultBuildDescriptor(Any(), "A build", "", System.currentTimeMillis())

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(BuildProgressDescriptorImpl(buildDescriptor))
        .message("Root message", "Text of the root message console", INFO, null)
      .progress("Running…")
        .startChildProgress("Inner progress")
        .cancel()
      .cancel()
    // @formatter:on

    assertBuildViewTree(buildView) {
      assertNode("cancelled") {
        assertConsoleText("")
        assertNode("Root message") {
          assertConsoleText("Text of the root message console\n")
        }
        assertNode("Inner progress") {
          assertConsoleText("")
        }
      }
    }
  }

  @Test
  fun `test build view listeners`(@TestDisposable testDisposable: Disposable) {
    val buildDescriptor = DefaultBuildDescriptor(Any(), "A build", "", System.currentTimeMillis())

    val buildMessages = mutableListOf<String>()
    //BuildViewManager
    project.service<BuildViewManager>().addListener(
      BuildProgressListener { _, event -> buildMessages.add(event.message) },
      testDisposable
    )

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start(BuildProgressDescriptorImpl(buildDescriptor))
        .output("Build greeting\n", ProcessOutputType.STDOUT)
        .message("Root message", "Text of the root message console", INFO, null)
      .progress("Running…")
        .startChildProgress("Inner progress")
          .output("inner progress output", ProcessOutputType.STDOUT)
          .fileMessage("File message1", "message1 descriptive text", INFO, FilePosition(Path.of("aFile.java"), 0, 0))
          .fileMessage("File message2", "message2 descriptive text", INFO, FilePosition(Path.of("aFile.java"), 0, 0))
        .finish()
      .output("Build farewell", ProcessOutputType.STDOUT)
      .finish()
    // @formatter:on

    assertBuildViewTree(buildView) {
      assertNode("finished") {
        assertConsoleText("Build greeting\n" +
                          "Build farewell")
        assertNode("Root message") {
          assertConsoleText("Text of the root message console\n")
        }
        assertNode("Inner progress") {
          assertConsoleText("inner progress output")
          assertNode("aFile.java") {
            assertNode("File message1") {
              assertConsoleText("message1 descriptive text")
            }
            assertNode("File message2") {
              assertConsoleText("message2 descriptive text")
            }
          }
        }
      }
    }

    assertThat(buildMessages)
      .containsExactly(
        "running…",
        "Build greeting\n",
        "Root message",
        "Running…",
        "Inner progress",
        "inner progress output",
        "File message1",
        "File message2",
        "Inner progress",
        "Build farewell",
        "finished"
      )
  }

  @Test
  fun `test presentable build event`() {
    val buildDescriptor = DefaultBuildDescriptor(Any(), "A build", "", System.currentTimeMillis())

    val component = JButton("test button")
    val presentationData = object : BuildEventPresentationData {
      override fun getNodeIcon(): Icon = AllIcons.General.Add
      override fun consoleToolbarActions(): ActionGroup? = null
      override fun getExecutionConsole(): ExecutionConsole {
        return object : ExecutionConsole {
          override fun getComponent(): JComponent = component
          override fun getPreferredFocusableComponent(): JComponent = component
          override fun dispose() {}
        }
      }
    }

    // @formatter:off
    BuildViewManager
      .createBuildProgress(project)
      .start("started", BuildProgressDescriptorImpl(buildDescriptor))
        .presentable("my event", presentationData)
      .finish("finished", SuccessResultImpl())
    // @formatter:on

    assertBuildViewTree(buildView) {
      assertNode("finished") {
        assertNode("my event") {
          assertValue {
            val consoleComponent = it.consoleView.component
            assertThat(consoleComponent).isEqualTo(component)
            assertThat((consoleComponent as JButton).text).isEqualTo("test button")
          }
        }
      }
    }
  }
}
