// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console

import com.intellij.build.BuildTextConsoleView
import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.progress.BuildProgressDescriptorImpl
import com.intellij.build.progress.BuildRootProgressImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewNode
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewSelectionText
import com.intellij.platform.testFramework.assertion.consoleText
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class BuildConsoleViewImplV2Test {

  private val buildDescriptor = DefaultBuildDescriptor(Any(), "test descriptor", "fake path", 1L)
  private val treeConsoleView by testFixture {
    val project = projectFixture().init()
    val view = withContext(Dispatchers.EDT) {
      BuildTreeConsoleView(
        project,
        buildDescriptor,
        BuildTextConsoleView(project, true, emptyList()),
        TestBuildViewSettingsProvider
      )
    }
    initialized(view) {
      Disposer.dispose(view)
    }
  }

  @Test
  @RegistryKey(key = "build.console.max.inlays", value = "1")
  fun `test message rendered as plain text`() {
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
      .message("first", "first text", MessageEvent.Kind.ERROR, null)
      .message("second", "line 1\nline 2", MessageEvent.Kind.ERROR, null)
      .finish("build finished", SuccessResultImpl())
    assertBuildViewNode(treeConsoleView, "first") {
      assertThat(it.consoleText).isEqualTo("first text\n")
    }
    assertBuildViewNode(treeConsoleView, "second") {
      assertThat(it.consoleText).isEqualTo("line 1\nline 2\n")
    }
    assertBuildViewSelectionText(treeConsoleView, "second") {
      assertThat(it).isEqualTo("line 1\nline 2")
    }
    invokeAndWaitIfNeeded {
      val editor = treeConsoleView.currentConsoleEditor!!
      assertThat(editor.document.text)
        .contains("line 1\nline 2")
        .doesNotContain("first text")
        .doesNotContain("<br/>")
      assertThat(editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength))
        .hasSize(1)
    }
  }

  private object TestBuildViewSettingsProvider : BuildViewSettingsProvider {
    override fun isSingleBuildConsoleView(): Boolean = true
  }
}
