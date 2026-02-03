// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleCustomImlDataEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


private object TestEntitySource : EntitySource

@TestApplication
class PsiVFSListenerTest {

  val projectFixture = projectFixture(openAfterCreation = true)

  @Test
  fun testWsmEventGeneratesExactlyOnePsiEvent(@TestDisposable testDisposable: Disposable) = runBlocking {
    val project = projectFixture.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    val psiListener = PsiEventsTestListener()
    val rootListener = ModuleRootTestListener()

    project.messageBus.connect(testDisposable).subscribe(ModuleRootListener.TOPIC, rootListener)
    PsiManagerEx.getInstanceEx(project).addPsiTreeChangeListener(psiListener, testDisposable)

    val wsm = WorkspaceModel.getInstance(project)
    val module = ModuleEntity("Test", emptyList(), TestEntitySource)
    wsm.update("add module in test") {
      it.addEntity(module)
    }

    assertEquals("beforeRootsChange\nrootsChanged\n", rootListener.eventsString)
    assertEquals("beforePropertyChange roots\npropertyChanged roots\n", psiListener.eventsString)

    IndexingTestUtil.suspendUntilIndexesAreReady(project) // index just added module (dumb mode may generate psi events)

    val imlData = ModuleCustomImlDataEntity(emptyMap(), TestEntitySource) {
      this.module = module
    }
    rootListener.reset()
    psiListener.reset()

    // intention: check PsiVFSListener generates PSI events correctly when WSM event is triggered, but RootsChangeEvent is not
    wsm.update("add custom iml data in test") {
      it.addEntity(imlData)
    }

    assertEquals("", rootListener.eventsString) // check that RootsChangeEvent is indeed not triggered
    assertEquals("beforePropertyChange roots\npropertyChanged roots\n", psiListener.eventsString)
  }

  @Test
  fun testMakeRootsChangeTotalRescanGeneratesExactlyOnePsiEvent(@TestDisposable testDisposable: Disposable) {
    testMakeRootsChangeGeneratesExactlyOnePsiEvent(testDisposable, RootsChangeRescanningInfo.TOTAL_RESCAN)
  }

  @Test
  fun testMakeRootsChangeNoRescanGeneratesExactlyOnePsiEvent(@TestDisposable testDisposable: Disposable) {
    testMakeRootsChangeGeneratesExactlyOnePsiEvent(testDisposable, RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
  }

  private fun testMakeRootsChangeGeneratesExactlyOnePsiEvent(testDisposable: Disposable, changes: RootsChangeRescanningInfo): Unit = runBlocking {
    val project = projectFixture.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    val psiListener = PsiEventsTestListener()
    val rootListener = ModuleRootTestListener()

    project.messageBus.connect(testDisposable).subscribe(ModuleRootListener.TOPIC, rootListener)
    PsiManagerEx.getInstanceEx(project).addPsiTreeChangeListener(psiListener, testDisposable)

    writeAction {
      ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), changes)
    }

    assertEquals(rootListener.eventsString, "beforeRootsChange\nrootsChanged\n")
    assertEquals(psiListener.eventsString, "beforePropertyChange roots\npropertyChanged roots\n")
  }
}