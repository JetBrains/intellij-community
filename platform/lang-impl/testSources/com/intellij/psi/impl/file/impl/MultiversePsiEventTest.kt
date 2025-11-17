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
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.ref.Reference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class MultiversePsiEventTest {
  companion object {
    val projectFixture = projectFixture(openAfterCreation = true).withSharedSourceEnabled()

    private val module1 = projectFixture.moduleFixture("m1")
    private val module2 = projectFixture.moduleFixture("m2")

    private val sourceRoot = sharedSourceRootFixture(module1, module2)
    private val sourceRoot2 = sharedSourceRootFixture(module1, module2)
    private val m1sourceRoot = module1.sourceRootFixture()

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
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
          if (event.child?.containingFile?.virtualFile == virtualFile) {
            collector.add(event)
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
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun childRemoved(event: PsiTreeChangeEvent) {
          collector.add(event)
        }
      }
    },
    updateBlock = { file -> file.delete(this) },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 before-property-change rename events on renaming file with 2 psi files`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun beforePropertyChange(event: PsiTreeChangeEvent) {
          collector.add(event)
        }
      }
    },
    updateBlock = { file -> file.rename(this, "B.java") },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 property-change rename events on renaming file with 2 psi files`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun propertyChanged(event: PsiTreeChangeEvent) {
          collector.add(event)
        }
      }
    },
    updateBlock = { file -> file.rename(this, "B.java") },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 1 delete event on renaming file to another file type with 2 psi files`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun childRemoved(event: PsiTreeChangeEvent) {
          collector.add(event)
        }
      }
    },
    updateBlock = { file -> file.rename(this, "A.fake") },
    expectedEventNumber = 1
  )

  @Test
  fun `test we receive 1 replaced event on renaming file to another file type with 2 psi files`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun childReplaced(event: PsiTreeChangeEvent) {
          collector.add(event)
        }
      }
    },
    updateBlock = { file ->
      file.rename(this, "A.fake")
    },
    expectedEventNumber = 1
  )

  @Test
  fun `test we receive 2 writeable event on changing writable status of a file with 2 psi files`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun propertyChanged(event: PsiTreeChangeEvent) {
          if (event.propertyName == PsiTreeChangeEvent.PROP_WRITABLE) {
            collector.add(event)
          }
        }
      }
    },
    updateBlock = { file ->
      file.isWritable = false
    },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 before-writeable event on changing writable status of a file with 2 psi files`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun beforePropertyChange(event: PsiTreeChangeEvent) {
          if (event.propertyName == PsiTreeChangeEvent.PROP_WRITABLE) {
            collector.add(event)
          }
        }
      }
    },
    updateBlock = { file ->
      file.isWritable = false
    },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 property-change events on changing encoding of a file with 2 psi files`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun propertyChanged(event: PsiTreeChangeEvent) {
          if (event.propertyName == VirtualFile.PROP_ENCODING) {
            collector.add(event)
          }
        }
      }
    },
    updateBlock = { file ->
      file.charset = Charsets.UTF_16
    },
    awaitCondition = { ec -> ec.events.size >= 2 },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 0 before-property-change events on changing encoding of a file with 2 psi files`() {
    val afterEventFlag = AtomicBoolean(false)
    doChangeTest(
      listenerFactory = { collector ->
        object : PsiTreeChangeAdapter() {
          override fun beforePropertyChange(event: PsiTreeChangeEvent) {
            if (event.propertyName == VirtualFile.PROP_ENCODING) {
              collector.add(event)
            }
          }

          override fun propertyChanged(event: PsiTreeChangeEvent) {
            afterEventFlag.set(true)
          }
        }
      },
      updateBlock = { file ->
        file.charset = Charsets.UTF_16
      },
      awaitCondition = { _ -> afterEventFlag.get() },
      expectedEventNumber = 0
    )
  }

  @Test
  fun `test we receive 2 before children changed events on updating content of a file with 2 psi files and WITHOUT a document`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
          if (!(event as PsiTreeChangeEventImpl).isGenericChange) {
            collector.add(event)
          }
        }
      }
    },
    updateBlock = { file ->
      file.setBinaryContent("class Baz {}".toByteArray())
    },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 children changed events on updating content of a file with 2 psi files and WITHOUT a document`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun childrenChanged(event: PsiTreeChangeEvent) {
          if (!(event as PsiTreeChangeEventImpl).isGenericChange) {
            collector.add(event)
          }
        }
      }
    },
    updateBlock = { file ->
      file.setBinaryContent("class Baz {}".toByteArray())
    },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 before-child-moved events on moving a file with 2 psi files`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun beforeChildMovement(event: PsiTreeChangeEvent) {
          collector.add(event)
        }
      }
    },
    updateBlock = { file ->
      file.move(this, sourceRoot2.get().virtualFile)
    },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 2 child moved events on moving a file with 2 psi files`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun childMoved(event: PsiTreeChangeEvent) {
          collector.add(event)
        }
      }
    },
    updateBlock = { file ->
      file.move(this, sourceRoot2.get().virtualFile)
    },
    expectedEventNumber = 2
  )

  @Test
  fun `test we receive 1 child moved event (and 1 child removed event) on moving a file with 2 psi files to a directory where only 1 context exists`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun childMoved(event: PsiTreeChangeEvent) {
          collector.add(event)
        }
      }
    },
    updateBlock = { file ->
      file.move(this, m1sourceRoot.get().virtualFile)
    },
    expectedEventNumber = 1
  )

  @Test
  fun `test we receive (1 child moved event and) 1 child removed event on moving a file with 2 psi files to a directory where only 1 context exists`() = doChangeTest(
    listenerFactory = { collector ->
      object : PsiTreeChangeAdapter() {
        override fun childRemoved(event: PsiTreeChangeEvent) {
          collector.add(event)
        }
      }
    },
    updateBlock = { file ->
      file.move(this, m1sourceRoot.get().virtualFile)
    },
    expectedEventNumber = 1
  )

  private fun doChangeTest(
    listenerFactory: (EventCollector) -> PsiTreeChangeListener,
    updateBlock: (file: VirtualFile) -> Unit,
    awaitCondition: (EventCollector) -> Boolean = { true },
    @Suppress("SameParameterValue") expectedEventNumber: Int,
  ) = runTest {
    val collector = EventCollector()
    val listener = listenerFactory(collector)

    psiManager.addPsiTreeChangeListenerBackgroundable(listener, testDisposable)

    val f1 = findPsiFile(c1)
    val f2 = findPsiFile(c2)

    writeAction {
      updateBlock(virtualFile)
    }

    while (!awaitCondition(collector)) {
      delay(50.milliseconds)
    }

    Reference.reachabilityFence(f1)
    Reference.reachabilityFence(f2)

    val events = collector.events
    assertThat(events.size).isEqualTo(expectedEventNumber)

    // Check that events correspond to different psi trees
    if (expectedEventNumber >= 2) {
      val psiFiles = events.mapNotNull { it.element ?: it.child ?: it.parent }.distinct()
      assertThat(psiFiles).hasSize(2).containsExactlyInAnyOrder(f1, f2)
    }
  }

  private fun runTest(block: suspend () -> Unit) = timeoutRunBlocking {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    block()
  }

  private class EventCollector {
    private val _events: MutableList<PsiTreeChangeEvent> = mutableListOf()

    fun add(event: PsiTreeChangeEvent) {
      _events.add(event)
    }

    val events: List<PsiTreeChangeEvent>
      get() = _events
  }
}