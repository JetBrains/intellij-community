// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.asDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class WelcomeScreenCommandLineProjectOpenProcessorTest {
  private val projectFixture = projectFixture(openAfterCreation = true)

  @TestDisposable
  lateinit var disposable: Disposable

  private val project: Project
    get() = projectFixture.get()

  private lateinit var provider: TestWelcomeScreenProjectProvider

  @BeforeEach
  fun setUp() {
    provider = TestWelcomeScreenProjectProvider()
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName<WelcomeScreenProjectProvider>("com.intellij.welcomeScreenProjectProvider"),
      listOf(provider),
      disposable,
    )
  }

  @Test
  @Timeout(30)
  fun `first and subsequent files reuse welcome project`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val firstFile = createFile(tempDir, "first.txt", "first")
    val secondFile = createFile(tempDir, "second.txt", "second")
    var projectCreationCount = 0
    val processor = createProcessor {
      projectCreationCount++
      provider.welcomeProject = project
      project
    }

    assertSame(project, processor.openProjectAndFile(firstFile, tempProject = false, OpenProjectTask()))
    assertSame(project, processor.openProjectAndFile(secondFile, tempProject = false, OpenProjectTask()))
    assertEquals(1, projectCreationCount)

    withContext(Dispatchers.EDT) {
      val fileEditorManager = FileEditorManager.getInstance(project)
      assertTrue(fileEditorManager.isFileOpen(findVirtualFile(firstFile)))
      assertTrue(fileEditorManager.isFileOpen(findVirtualFile(secondFile)))
    }
  }

  @Test
  @Timeout(30)
  fun `caret position is preserved`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val file = createFile(tempDir, "position.txt", "first line\nsecond line\n")
    val processor = createProcessor {
      provider.welcomeProject = project
      project
    }

    processor.openProjectAndFile(file, tempProject = false, OpenProjectTask {
      line = 2
      column = 3
    })

    withContext(Dispatchers.EDT) {
      val position = FileEditorManager.getInstance(project).selectedTextEditor?.caretModel?.logicalPosition
      assertEquals(1, position?.line)
      assertEquals(3, position?.column)
    }
  }

  @Test
  @Timeout(30)
  @SystemProperty("idea.trust.headless.disabled", "false")
  fun `an opened file is in the safe mode before the editor opens`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val file = createFile(tempDir, "external.txt", "text")
    val processor = createProcessor {
      provider.welcomeProject = project
      project
    }

    assertSame(project, processor.openProjectAndFile(file, tempProject = false, OpenProjectTask {}))

    val virtualFile = findVirtualFile(file)
    assertFalse(TrustedFiles.isTrusted(virtualFile, project))
    withContext(Dispatchers.EDT) {
      assertTrue(FileEditorManager.getInstance(project).isFileOpen(virtualFile))
    }
  }

  @Test
  @Timeout(30)
  fun `temp project falls back to next processor`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val file = createFile(tempDir, "temporary.txt", "text")
    var projectCreationCount = 0
    val processor = createProcessor {
      projectCreationCount++
      project
    }

    assertNull(processor.openProjectAndFile(file, tempProject = true, OpenProjectTask()))
    assertEquals(0, projectCreationCount)
  }

  @Test
  @Timeout(60)
  fun `focused normal project is preferred to welcome project`(@TempDir tempDir: Path): Unit = timeoutRunBlocking(30.seconds) {
    val file = createFile(tempDir, "focused.txt", "text")
    var projectCreationCount = 0
    val processor = createProcessor(
      getFocusedProject = { project },
      createProject = {
        projectCreationCount++
        project
      },
    )

    assertSame(project, processor.openProjectAndFile(file, tempProject = false, OpenProjectTask()))
    assertEquals(0, projectCreationCount)

    withContext(Dispatchers.EDT) {
      assertTrue(FileEditorManager.getInstance(project).isFileOpen(findVirtualFile(file)))
    }
  }

  private fun createProcessor(
    getFocusedProject: () -> Project? = { null },
    createProject: suspend (WelcomeScreenProjectProvider) -> Project,
  ): WelcomeScreenCommandLineProjectOpenProcessor {
    return WelcomeScreenCommandLineProjectOpenProcessor(
      getOpenProjects = { provider.welcomeProject?.let { arrayOf(it) } ?: emptyArray() },
      getFocusedProject = getFocusedProject,
      createWelcomeScreenProject = createProject,
    )
  }

  private fun createFile(tempDir: Path, name: String, content: String): Path {
    return Files.writeString(tempDir.resolve(name), content)
  }

  private fun findVirtualFile(path: Path) = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!

  private class TestWelcomeScreenProjectProvider : WelcomeScreenProjectProvider() {
    var welcomeProject: Project? = null

    override fun canOpenFilesFromSystemFileManager(filePath: Path): Boolean = Files.isRegularFile(filePath)

    override fun getWelcomeScreenProjectName(): String = "TestWelcomeProject"

    override fun doIsWelcomeScreenProject(project: Project): Boolean = project === welcomeProject

    override fun doIsForceDisabledFileColors(): Boolean = true

    override fun doGetCreateNewFileProjectPrefix(): String = "testProject"
  }
}
