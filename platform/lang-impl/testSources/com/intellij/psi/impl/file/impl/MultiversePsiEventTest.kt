// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.lang.fakeLang.registerFakeLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.ref.Reference
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
internal class MultiversePsiEventTest {
  companion object {
    val projectFixture = projectFixture(openAfterCreation = true).withSharedSourceEnabled()

    private val module1 = projectFixture.moduleFixture("m1")
    private val module2 = projectFixture.moduleFixture("m2")

    private val sourceRoot = sharedSourceRootFixture(module1, module2)

    @Suppress("unused")
    private val registerFakeLang = testFixture {
      val newDisposable = Disposer.newDisposable()
      withContext(Dispatchers.EDT) {
        registerFakeLanguage(newDisposable)
      }
      initialized(Unit) {
        withContext(Dispatchers.EDT) {
          Disposer.dispose(newDisposable)
        }
      }
    }
  }

  private val testDisposable by disposableFixture()

  private val virtualFile by sourceRoot.virtualFileFixture("Foo.java", "class Foo {}")
  private val project by projectFixture

  private val m1 by lazy { ModuleManager.getInstance(project).findModuleByName("m1")!! }
  private val m2 by lazy { ModuleManager.getInstance(project).findModuleByName("m2")!! }

  private val psiManager by lazy { PsiManagerEx.getInstanceEx(project) }

  private suspend fun findPsiFile(context: CodeInsightContext): PsiFile = requireNotNull(readAction { psiManager.findFile(virtualFile, context) })

  private val c1 = ProjectModelContextBridge.getInstance(project).getContext(m1)!!
  private val c2 = ProjectModelContextBridge.getInstance(project).getContext(m2)!!


  @Test
  fun `test we receive 2 before-delete events on deleting file with 2 psi-files`() = doChangeTest(
    listenerFactory = { counter ->
      object : PsiTreeChangeAdapter() {
        override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
          if (event.child?.containingFile?.virtualFile == virtualFile) {
            counter.incrementAndGet()
          }
        }
      }
    },
    updateBlock = { file ->
      file.delete(this)
    },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 delete events on deleting file with 2 psi files`() = doChangeTest(
    listenerFactory = { counter ->
      object : PsiTreeChangeAdapter() {
        override fun childRemoved(event: PsiTreeChangeEvent) {
          counter.incrementAndGet()
        }
      }
    },
    updateBlock = { file -> file.delete(this) },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 before-property-change rename events on renaming file with 2 psi files`() = doChangeTest(
    listenerFactory = { counter ->
      object : PsiTreeChangeAdapter() {
        override fun beforePropertyChange(event: PsiTreeChangeEvent) {
          counter.incrementAndGet()
        }
      }
    },
    updateBlock = { file -> file.rename(this, "B.java") },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 property-change rename events on renaming file with 2 psi files`() = doChangeTest(
    listenerFactory = { counter ->
      object : PsiTreeChangeAdapter() {
        override fun propertyChanged(event: PsiTreeChangeEvent) {
          counter.incrementAndGet()
        }
      }
    },
    updateBlock = { file -> file.rename(this, "B.java") },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 1 delete event on renaming file to another file type with 2 psi files`() = doChangeTest(
    listenerFactory = { counter ->
      object : PsiTreeChangeAdapter() {
        override fun childRemoved(event: PsiTreeChangeEvent) {
          counter.incrementAndGet()
        }
      }
    },
    updateBlock = { file -> file.rename(this, "A.fake") },
    expectedEventNumber = 1
  )

  @Test
  fun `test we receive 1 replaced event on renaming file to another file type with 2 psi files`() = doChangeTest(
    listenerFactory = { counter ->
      object : PsiTreeChangeAdapter() {
        override fun childReplaced(event: PsiTreeChangeEvent) {
          counter.incrementAndGet()
        }
      }
    },
    updateBlock = { file ->
      file.rename(this, "A.fake")
    },
    expectedEventNumber = 1
  )

  private fun doChangeTest(
    listenerFactory: (AtomicInteger) -> PsiTreeChangeListener,
    updateBlock: (file: VirtualFile) -> Unit,
    @Suppress("SameParameterValue") expectedEventNumber: Int
  ) = runTest {
    val counter = AtomicInteger(0)
    val listener = listenerFactory(counter)

    psiManager.addPsiTreeChangeListenerBackgroundable(listener, testDisposable)

    val f1 = findPsiFile(c1)
    val f2 = findPsiFile(c2)

    writeAction {
      updateBlock(virtualFile)
    }

    Reference.reachabilityFence(f1)
    Reference.reachabilityFence(f2)

    assertThat(counter.get()).isEqualTo(expectedEventNumber)
  }

  private fun runTest(block: suspend () -> Unit) = timeoutRunBlocking {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    block()
  }
}