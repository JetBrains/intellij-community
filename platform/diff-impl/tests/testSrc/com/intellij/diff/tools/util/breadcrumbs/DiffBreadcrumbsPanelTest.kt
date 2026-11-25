// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.breadcrumbs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class DiffBreadcrumbsPanelTest {
  private val projectFixture: TestFixture<Project> = projectFixture()

  @Test
  fun `updates use the first dispatch when modality permits`(@TestDisposable disposable: Disposable): Unit =
    checkModality(unrelatedModalOpen = false, disposable)

  @Test
  fun `updates wait for the component modality`(@TestDisposable disposable: Disposable): Unit =
    checkModality(unrelatedModalOpen = true, disposable)

  private fun checkModality(unrelatedModalOpen: Boolean, disposable: Disposable): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument("text"), project)
      val panelDisposable = Disposer.newDisposable(disposable)
      try {
        var updates = 0
        val panel = object : DiffBreadcrumbsPanel(editor, panelDisposable) {
          override fun updateCollectors(enabled: Boolean): Boolean {
            updates++
            return false
          }

          override fun computeCrumbs(offset: Int): Iterable<Crumb>? = null
        }
        UIUtil.dispatchAllInvocationEvents()
        updates = 0
        var updatesAtMarker = -1
        val unrelatedModal = Any()
        if (unrelatedModalOpen) LaterInvocator.enterModal(unrelatedModal)
        try {
          requestUpdateAndQueueMarker(panel, project) { updatesAtMarker = updates }
          UIUtil.dispatchAllInvocationEvents()
          if (unrelatedModalOpen) {
            assertEquals(0, updates)
            assertEquals(0, updatesAtMarker)
          }
          else {
            assertTrue(updatesAtMarker > 0)
          }
        }
        finally {
          if (unrelatedModalOpen) LaterInvocator.leaveModal(unrelatedModal)
        }
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(updates > 0)
      }
      finally {
        Disposer.dispose(panelDisposable)
        factory.releaseEditor(editor)
      }
    }
  }

  /** Schedules the update and the marker on the same invocation queue. */
  @Suppress("ForbiddenInSuspectContextMethod")
  private fun requestUpdateAndQueueMarker(panel: DiffBreadcrumbsPanel, project: Project, marker: Runnable) {
    panel.setCrumbsShown(true)
    ApplicationManager.getApplication().invokeLater(marker, ModalityState.any(), project.disposed)
  }
}
