// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

@TestApplication
internal class LspProviderRemovalTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Test
  fun `a provider that is removed before its start runs gets no fileOpened call`() = timeoutRunBlocking(2.minutes) {
    codeInsightFixture.configureByText("removed.txt", "content")
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val provider = RecordingLspProvider()
    val jobsBefore = manager.cs.coroutineContext.job.children.toSet()

    // the same write action adds and removes the provider, as a plugin unload removes the provider after its start
    edtWriteAction {
      val registration = Disposer.newDisposable("LspProviderRemovalTest")
      LspIntegrationProvider.EP_NAME.point.registerExtension(provider, registration)
      Disposer.dispose(registration)
    }
    // the start of the provider runs in a coroutine of the manager, after the write action
    (manager.cs.coroutineContext.job.children - jobsBefore).forEach { it.join() }

    assertEquals(emptyList<VirtualFile>(), provider.openedFiles.toList(), "the removed provider must get no fileOpened call")
  }
}

private class RecordingLspProvider : LspIntegrationProvider {
  val openedFiles: MutableList<VirtualFile> = CopyOnWriteArrayList()

  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    openedFiles.add(file)
  }
}
