// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.ide.JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES
import com.intellij.ide.awaitFileNotInSourceRootChecks
import com.intellij.ide.setFileNotInSourceRootCheckListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class FileNotInSourceRootCheckerTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture("sample")
  private val warning = JavaBundle.message("warning.java.file.outside.source.root")

  @Nested
  inner class SharedFileProjects {
    private val otherProjectFixture = projectFixture(openAfterCreation = true)
    private val otherModuleFixture = otherProjectFixture.moduleFixture("other")

    @Test
    fun openingFileInAnotherProjectDoesNotCancelPendingRefresh(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
      val project = projectFixture.get()
      val otherProject = otherProjectFixture.get()
      val module = moduleFixture.get()
      val contentRoot = createDirectory("src")
      addSourceRoot(module, contentRoot, createDirectory("src/kotlin"))
      val javaRoot = createDirectory("src/java")
      val javaFile = createJavaFile(javaRoot, "public class JavaClass {}")
      addSourceRoot(otherModuleFixture.get(), contentRoot, createDirectory("src/kotlin"))
      val otherManager = FileEditorManager.getInstance(otherProject)

      withOpenFile(javaFile) {
        assertEquals(1, warningCount(javaFile))
        try {
          withFirstRefreshSuspended { awaitRefreshComputed ->
            addSourceRoot(module, contentRoot, javaRoot)
            awaitRefreshComputed()
            withContext(Dispatchers.EDT) { otherManager.openFile(javaFile, FileEditorOpenRequest.withFocus(true)) }
            awaitFileNotInSourceRootChecks(project, pendingChecks = 1)
          }
          assertEquals(0, warningCount(javaFile))
        }
        finally {
          withContext(Dispatchers.EDT) { otherManager.closeFile(javaFile) }
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun pendingRefreshFollowsMove(moveDirectory: Boolean): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val contentRoot = createDirectory("src")
    addSourceRoot(module, contentRoot, createDirectory("src/kotlin"))
    val javaRoot = createDirectory("src/java")
    val directory = createDirectory("src/java/pack")
    val outsideDirectory = createDirectory("src/outside")
    val javaFile = createJavaFile(directory, "public class JavaClass {}")

    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))
      withFirstRefreshSuspended { awaitRefreshComputed ->
        addSourceRoot(module, contentRoot, javaRoot)
        awaitRefreshComputed()
        moveFile(if (moveDirectory) directory else javaFile, outsideDirectory)
        // The check of the move applies its result before the stale refresh continues.
        awaitFileNotInSourceRootChecks(project, pendingChecks = 1)
      }
      assertEquals(1, warningCount(javaFile))
    }
  }

  @Test
  fun staleRefreshDoesNotOverwriteNewerRefresh(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val contentRoot = createDirectory("src")
    addSourceRoot(module, contentRoot, createDirectory("src/kotlin"))
    val otherSourceRoot = createDirectory("src/other")
    val javaRoot = createDirectory("src/java")
    val javaFile = createJavaFile(createDirectory("src/java/pack"), "public class JavaClass {}")

    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))
      withFirstRefreshSuspended { awaitRefreshComputed ->
        addSourceRoot(module, contentRoot, otherSourceRoot)
        awaitRefreshComputed()
        addSourceRoot(module, contentRoot, javaRoot)
        awaitFileNotInSourceRootChecks(project, pendingChecks = 1)
        assertEquals(0, rawWarningCount(javaFile))
      }
      assertEquals(0, warningCount(javaFile))
    }
  }

  @Test
  fun closeEditorWhileRefreshIsPending(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val contentRoot = createDirectory("src")
    addSourceRoot(module, contentRoot, createDirectory("src/kotlin"))
    val otherSourceRoot = createDirectory("src/other")
    val javaFile = createJavaFile(contentRoot, "public class JavaClass {}")

    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))
      withFirstRefreshSuspended { awaitRefreshComputed ->
        addSourceRoot(module, contentRoot, otherSourceRoot)
        awaitRefreshComputed()
        withContext(Dispatchers.EDT) { FileEditorManager.getInstance(project).closeFile(javaFile) }
      }
      awaitFileNotInSourceRootChecks(project)
    }
    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))
    }
  }

  @Test
  fun ignoreProjectWhileRefreshIsPending(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    val contentRoot = createDirectory("src")
    addSourceRoot(module, contentRoot, createDirectory("src/kotlin"))
    val otherSourceRoot = createDirectory("src/other")
    val javaFile = createJavaFile(contentRoot, "public class JavaClass {}")

    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))

      edtWriteAction {
        val psiFile = requireNotNull(PsiManager.getInstance(project).findFile(javaFile))
        val editor = requireNotNull(FileEditorManager.getInstance(project).selectedTextEditor)
        val highlight = analyzer().getFileLevelHighlights(project, psiFile).single { it.description == warning }
        val ignoreText = JavaBundle.message("intention.family.name.ignore.project")
        val ignore = requireNotNull(highlight.findRegisteredQuickFix { descriptor, _ ->
          descriptor.getOptions(psiFile, editor).firstOrNull { it.text == ignoreText }
        })
        // The roots change starts a refresh. The refresh cannot finish before this write action ends.
        addSourceRoot(module, contentRoot, otherSourceRoot)
        ignore.invoke(project, editor, psiFile)
      }
      awaitFileNotInSourceRootChecks(project)

      assertTrue(PropertiesComponent.getInstance(project).getBoolean(JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES, false))
      assertEquals(0, warningCount(javaFile))
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun dismissWarningUntilFileIsClosed(refreshPending: Boolean): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val module = moduleFixture.get()
    val contentRoot = createDirectory("src")
    addSourceRoot(module, contentRoot, createDirectory("src/kotlin"))
    val otherSourceRoot = createDirectory("src/other")
    val additionalSourceRoot = createDirectory("src/additional")
    val outsideDirectory = createDirectory("src/outside")
    val javaFile = createJavaFile(contentRoot, "public class JavaClass {}")

    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))
      if (refreshPending) {
        withFirstRefreshSuspended { awaitRefreshComputed ->
          addSourceRoot(module, contentRoot, otherSourceRoot)
          awaitRefreshComputed()
          dismissWarning(javaFile)
          assertEquals(0, rawWarningCount(javaFile))
        }
      }
      else {
        dismissWarning(javaFile)
      }
      assertEquals(0, warningCount(javaFile))

      addSourceRoot(module, contentRoot, additionalSourceRoot)
      assertEquals(0, warningCount(javaFile))

      moveFile(javaFile, outsideDirectory)
      assertEquals(0, warningCount(javaFile))
    }
    withContext(Dispatchers.EDT) {
      assertTrue(FileEditorManager.getInstance(projectFixture.get()).getAllEditors(javaFile).isEmpty())
    }
    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))
    }
  }

  @Test
  fun bannerUpdatesWhenSourceRootChanges(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val module = moduleFixture.get()
    val contentRoot = createDirectory("src")
    addSourceRoot(module, contentRoot, createDirectory("src/jvmMain/kotlin"))
    val javaRoot = createDirectory("src/jvmMain/java")
    val javaFile = createJavaFile(createDirectory("src/jvmMain/java/pack"), "package pack; public class JavaClass {}")

    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))

      addSourceRoot(module, contentRoot, javaRoot)
      assertEquals(0, warningCount(javaFile))

      ModuleRootModificationUtil.updateModel(module) { model ->
        val entry = model.contentEntries.single { it.file == contentRoot }
        entry.removeSourceFolder(entry.sourceFolders.single { it.file == javaRoot })
      }
      assertEquals(1, warningCount(javaFile))
    }
  }

  @Test
  fun bannerFollowsFileMoveAfterRefresh(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val module = moduleFixture.get()
    val contentRoot = createDirectory("src")
    val sourceRoot = createDirectory("src/kotlin")
    addSourceRoot(module, contentRoot, sourceRoot)
    val outsideDirectory = createDirectory("src/outside")
    val javaFile = createJavaFile(contentRoot, "public class JavaClass {}")

    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))

      // The move listener must still work after a refresh.
      addSourceRoot(module, contentRoot, createDirectory("src/other"))
      assertEquals(1, warningCount(javaFile))

      moveFile(javaFile, outsideDirectory)
      assertEquals(1, warningCount(javaFile))

      moveFile(javaFile, sourceRoot)
      assertEquals(0, warningCount(javaFile))
    }
  }

  @Test
  fun bannerFollowsDirectoryMoveAfterRefresh(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val module = moduleFixture.get()
    val contentRoot = createDirectory("src")
    val sourceRoot = createDirectory("src/kotlin")
    addSourceRoot(module, contentRoot, sourceRoot)
    val outsideDirectory = createDirectory("src/outside")
    val directory = createDirectory("src/outside/pack")
    val javaFile = createJavaFile(directory, "package pack; public class JavaClass {}")

    withOpenFile(javaFile) {
      assertEquals(1, warningCount(javaFile))

      addSourceRoot(module, contentRoot, createDirectory("src/other"))
      assertEquals(1, warningCount(javaFile))

      moveFile(directory, sourceRoot)
      assertEquals(0, warningCount(javaFile))

      moveFile(directory, outsideDirectory)
      assertEquals(1, warningCount(javaFile))
    }
  }

  private fun analyzer(): DaemonCodeAnalyzerImpl =
    DaemonCodeAnalyzerEx.getInstanceEx(projectFixture.get()) as DaemonCodeAnalyzerImpl

  private suspend fun createDirectory(relativePath: String): VirtualFile = edtWriteAction {
    VfsUtil.createDirectories("${requireNotNull(projectFixture.get().basePath)}/$relativePath")
  }

  private suspend fun createJavaFile(directory: VirtualFile, text: String): VirtualFile = edtWriteAction {
    directory.createChildData(this, "JavaClass.java").also { VfsUtil.saveText(it, text) }
  }

  private fun addSourceRoot(module: Module, contentRoot: VirtualFile, sourceRoot: VirtualFile) {
    ModuleRootModificationUtil.updateModel(module) { model ->
      val entry = model.contentEntries.singleOrNull { it.file == contentRoot } ?: model.addContentEntry(contentRoot)
      entry.addSourceFolder(sourceRoot, false)
    }
  }

  private suspend fun moveFile(file: VirtualFile, directory: VirtualFile) {
    edtWriteAction { file.move(this, directory) }
  }

  private suspend fun warningCount(file: VirtualFile): Int {
    awaitFileNotInSourceRootChecks(projectFixture.get())
    return rawWarningCount(file)
  }

  private suspend fun dismissWarning(file: VirtualFile) {
    val project = projectFixture.get()
    withContext(Dispatchers.EDT) {
      val psiFile = requireNotNull(PsiManager.getInstance(project).findFile(file))
      val editor = requireNotNull(FileEditorManager.getInstance(project).selectedTextEditor)
      val highlight = analyzer().getFileLevelHighlights(project, psiFile).single { it.description == warning }
      val dismissText = JavaBundle.message("intention.family.name.dismiss")
      val dismiss = requireNotNull(highlight.findRegisteredQuickFix { descriptor, _ ->
        descriptor.getOptions(psiFile, editor).firstOrNull { it.text == dismissText }
      })
      dismiss.invoke(project, editor, psiFile)
    }
  }

  /** Counts the warnings without a wait for the pending checks. */
  private suspend fun rawWarningCount(file: VirtualFile): Int {
    val project = projectFixture.get()
    return withContext(Dispatchers.EDT) {
      val psiFile = requireNotNull(PsiManager.getInstance(project).findFile(file))
      analyzer().getFileLevelHighlights(project, psiFile).count { it.description == warning }
    }
  }

  /** Suspends the first refresh after its background check until [block] completes. */
  private suspend fun withFirstRefreshSuspended(block: suspend (awaitRefreshComputed: suspend () -> Unit) -> Unit) {
    val project = projectFixture.get()
    val refreshComputed = CompletableDeferred<Unit>()
    val resumeRefresh = CompletableDeferred<Unit>()
    val refreshSuspended = AtomicBoolean()
    setFileNotInSourceRootCheckListener(project) { refresh ->
      if (refresh && refreshSuspended.compareAndSet(false, true)) {
        refreshComputed.complete(Unit)
        resumeRefresh.await()
      }
    }
    try {
      block { refreshComputed.await() }
    }
    finally {
      setFileNotInSourceRootCheckListener(project, null)
      resumeRefresh.complete(Unit)
    }
  }

  private suspend fun withOpenFile(file: VirtualFile, block: suspend () -> Unit) {
    val fileEditorManager = FileEditorManager.getInstance(projectFixture.get())
    withContext(Dispatchers.EDT) { fileEditorManager.openFile(file, FileEditorOpenRequest.withFocus(true)) }
    try {
      block()
    }
    finally {
      withContext(Dispatchers.EDT) { fileEditorManager.closeFile(file) }
    }
  }
}
