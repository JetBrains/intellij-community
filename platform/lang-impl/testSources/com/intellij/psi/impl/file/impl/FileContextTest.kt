// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersioningLockingListener
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
@EnableTracingFor(
  categoryClasses = [CodeInsightContextManagerImpl::class],
)
internal class FileContextTest {
  companion object {
    private val projectFixture = projectFixture().withSharedSourceEnabled()

    private val module1 = projectFixture.moduleFixture("FileContextTest_src1")
    private val module2 = projectFixture.moduleFixture("FileContextTest_src2")

    private val sourceRoot = sharedSourceRootFixture(module1, module2)
  }

  private val virtualFile by sourceRoot.virtualFileFixture("TestCommon.txt", "Test file Common")
  private val project by projectFixture

  private val psiManager by lazy { PsiManagerEx.getInstanceEx(project) }
  private val contextManager by lazy { CodeInsightContextManagerImpl.getInstanceImpl(project) }

  private fun findPsiFile(): PsiFile = requireNotNull(psiManager.findFile(virtualFile))
  private fun findPsiFile(context: CodeInsightContext): PsiFile = requireNotNull(psiManager.findFile(virtualFile, context))

  @Test
  fun testAnyContextByDefault() = timeoutRunBlocking {
    readAction {
      val file = findPsiFile()
      val rawContext = contextManager.getCodeInsightContextRaw(file.viewProvider)
      assertContextsEqual(anyContext(), rawContext)
    }
  }

  @Test
  fun testContextIsInferred() = timeoutRunBlocking {
    readAction {
      val file = findPsiFile()
      assertNotEquals(anyContext(), file.codeInsightContext)
    }
  }

  @Test
  fun testContextIsCorrectlySet() = timeoutRunBlocking {
    val context = module1.moduleContext()
    readAction {
      val file = findPsiFile(context)
      assertContextsEqual(context, file.codeInsightContext)
    }
  }

  @Test
  fun testRawContextIsCorrectlySet() = timeoutRunBlocking {
    val context = module1.moduleContext()
    readAction {
      val file = findPsiFile(context)
      assertContextsEqual(context, contextManager.getCodeInsightContextRaw(file.viewProvider))
    }
  }

  @Test
  fun testAnyContextIsPromotedToExactContext() = timeoutRunBlocking {
    val context1 = module1.moduleContext()

    readAction {
      val file = findPsiFile()
      val rawContext = contextManager.getCodeInsightContextRaw(file.viewProvider)

      assertContextsEqual(anyContext(), rawContext)

      val file1 = findPsiFile(context1)
      assertEquals(file1, file)

      val rawContext1 = contextManager.getCodeInsightContextRaw(file1.viewProvider)
      assertContextsEqual(context1, rawContext1)
    }
  }

  @Test
  fun testTwoContextsForFile() = timeoutRunBlocking {
    val context1 = module1.moduleContext()
    val context2 = module2.moduleContext()

    val fileWithRawContext = withTimeout(1.seconds) {
      // trying to get a file with "any" context.
      // some unrelated project activities can instantiate the proper context,
      // so let's just drop the PsiFile in that case and try again.
      var file: PsiFile
      do {
        ensureActive()

        file = readAction { findPsiFile() }
        val rawContext = contextManager.getCodeInsightContextRaw(file.viewProvider)
        if (rawContext == anyContext()) {
          break
        }

        edtWriteAction {
          psiManager.fileManager.setViewProvider(virtualFile, null)
        }
      }
      while (true)

      file
    }

    readAction {
      val file1 = findPsiFile(context1)
      val file2 = findPsiFile(context2)

      assertEquals(file1, fileWithRawContext)
      assertNotEquals(file1, file2)

      val rawContext1 = contextManager.getCodeInsightContextRaw(file1.viewProvider)
      val rawContext2 = contextManager.getCodeInsightContextRaw(file2.viewProvider)
      assertContextsEqual(context1, rawContext1)
      assertContextsEqual(context2, rawContext2)
    }
  }

  @Test
  fun testEventSystemDisabledFileHasSingleContextWithoutReadLock() = timeoutRunBlocking {
    // IJPL-247497: non-physical, event-system-disabled files must be queryable without a read lock
    // and are treated as single (default) context, even when shared-source support is enabled.
    val lightFile = LightVirtualFile("terminalLike.txt", PlainTextFileType.INSTANCE, "")

    // sanity: we hold no read access here, so the previous soft-assert would have thrown
    assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)

    assertContextsEqual(defaultContext(), contextManager.getPreferredContext(lightFile))
    assertContextsEqual(defaultContext(), contextManager.getCodeInsightContexts(lightFile).single())
  }

  @Test
  fun testContextsAreQueryableInFrozenPsiWithoutReadLock() = timeoutRunBlocking(context = Dispatchers.Default) {
    val context1 = module1.moduleContext()
    val context2 = module2.moduleContext()

    // Fill the caches under the read lock. The frozen block then checks only the access assertion.
    val (viewProvider, preferredContext) = readAction {
      assertEquals(setOf(context1, context2), contextManager.getCodeInsightContexts(virtualFile).toSet())
      findPsiFile(context1).viewProvider to contextManager.getPreferredContext(virtualFile)
    }

    // The test logger fails the test when ensureReadAccess reports a missing read lock.
    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()

      assertEquals(setOf(context1, context2), contextManager.getCodeInsightContexts(virtualFile).toSet())
      assertContextsEqual(preferredContext, contextManager.getPreferredContext(virtualFile))
      assertContextsEqual(context1, contextManager.getCodeInsightContext(viewProvider))
    }
  }

  @Test
  fun testContextsWithoutReadLockOutsideFrozenPsiReportError() = timeoutRunBlocking(context = Dispatchers.Default) {
    readAction { contextManager.getCodeInsightContexts(virtualFile) }

    assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)

    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      contextManager.getCodeInsightContexts(virtualFile)
    }
    assertTrue(error.message!!.contains(ThreadingAssertions.MUST_EXECUTE_IN_READ_ACTION)) { "Unexpected error: $error" }
  }

  @Test
  fun testParallelContextModificationDoesNotAffectFrozenPsi(@TestDisposable disposable: Disposable) = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val context1 = module1.moduleContext()
    val context2 = module2.moduleContext()
    val root = sourceRoot.get().virtualFile

    val (viewProvider, preferredContext) = readAction {
      assertEquals(setOf(context1, context2), contextManager.getCodeInsightContexts(virtualFile).toSet())
      findPsiFile(context1).viewProvider to contextManager.getPreferredContext(virtualFile)
    }

    try {
      PsiVersioningService.freezePsiVersion {
        ThreadingAssertions.assertNoReadAccess()
        assertEquals(setOf(context1, context2), contextManager.getCodeInsightContexts(virtualFile).toSet())

        // The write action runs on another thread. It removes the file from module2, and this invalidates all contexts.
        async(Dispatchers.Default) {
          removeRootFromModule(module2.get(), root)
        }.asCompletableFuture().get()

        assertEquals(setOf(context1, context2), contextManager.getCodeInsightContexts(virtualFile).toSet())
        assertContextsEqual(preferredContext, contextManager.getPreferredContext(virtualFile))
        assertContextsEqual(context1, contextManager.getCodeInsightContext(viewProvider))
      }

      readAction {
        assertEquals(listOf(context1), contextManager.getCodeInsightContexts(virtualFile))
      }
    }
    finally {
      addRootToModule(module2.get(), root)
    }

    readAction {
      assertEquals(setOf(context1, context2), contextManager.getCodeInsightContexts(virtualFile).toSet())
    }
  }
}

private fun installVersioningListeners(disposable: Disposable) {
  val listener = PsiVersioningLockingListener()
  val application = ApplicationManagerEx.getApplicationEx()
  application.addWriteActionListener(listener, disposable)
  application.addReadActionListener(listener, disposable)
  application.addWriteIntentReadActionListener(listener, disposable)
  application.addSuspendingWriteActionListener(listener, disposable)
}

private suspend fun removeRootFromModule(module: Module, root: VirtualFile) {
  edtWriteAction {
    ModuleRootModificationUtil.updateModel(module) { model ->
      val contentRoot = model.contentEntries.first { it.file == root }
      model.removeContentEntry(contentRoot)
    }
  }
}

private suspend fun addRootToModule(module: Module, root: VirtualFile) {
  edtWriteAction {
    ModuleRootModificationUtil.updateModel(module) { model ->
      if (model.contentEntries.none { it.file == root }) {
        model.addContentEntry(root).addSourceFolder(root, false)
      }
    }
  }
}

private fun assertContextsEqual(expectedContext: CodeInsightContext, actualContext: CodeInsightContext) {
  assertEquals(expectedContext, actualContext) { "Unexpected context: ${actualContext.asText()}. Expected: ${expectedContext.asText()}" }
}

private fun CodeInsightContext.asText() = when (this) {
  anyContext() -> "AnyContext"
  is ModuleContext -> "ModuleContext(${this.getModule()!!.name})"
  else -> this.toString()
}

fun sharedSourceRootFixture(vararg moduleFixtures: TestFixture<Module>): TestFixture<PsiDirectory> = testFixture("shared-source-root-fixture") {
  require(moduleFixtures.isNotEmpty())

  for (fixture in moduleFixtures) {
    fixture.init()
  }

  val firstFixture = moduleFixtures.first()
  val sharedSourceRoot = firstFixture.sourceRootFixture()
  val root = sharedSourceRoot.init().virtualFile

  // make sharedSourceRoot also the root of module 2
  edtWriteAction {
    for (fixture in moduleFixtures) {
      if (fixture === firstFixture) continue

      val module = fixture.get()
      val modifiableModel = module.rootManager.modifiableModel
      val contentRoot = modifiableModel.addContentEntry(root)
      contentRoot.addSourceFolder(root, false)
      modifiableModel.commit()
    }
  }

  initialized(sharedSourceRoot.init()) {
    edtWriteAction {
      for (fixture in moduleFixtures) {
        if (fixture === firstFixture) continue

        val module = fixture.get()
        if (!module.isDisposed) {
          ModuleRootModificationUtil.updateModel(module) { model ->
            val contentRoot = model.contentEntries.firstOrNull { it.file == root }
            if (contentRoot != null) {
              model.removeContentEntry(contentRoot)
            }
          }
        }
      }
    }
  }
}

internal suspend fun TestFixture<Module>.moduleContext(): CodeInsightContext {
  val module = this.get()
  val contextBridge = ProjectModelContextBridge.getInstanceAsync(module.project)
  val context = readAction { requireNotNull(contextBridge.getContext(module)) }
  return context
}
