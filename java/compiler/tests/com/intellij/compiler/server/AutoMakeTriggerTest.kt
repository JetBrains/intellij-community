// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText

/**
 * Tests which VFS changes trigger auto-make.
 *
 * An external Maven or Gradle build changes many files in the compiler output directory.
 * A removal outside project content must not trigger a build (IDEA-389429).
 *
 * Removals are classified before the VFS change and everything else after it.
 * Each test uses the same phase as the production code.
 *
 * `./tests.cmd --module intellij.java.compiler.tests.main --test com.intellij.compiler.server.AutoMakeTriggerTest`
 */
@TestApplication
class AutoMakeTriggerTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val workspace = tempPathFixture()
  private val projectFixture = projectFixture()
  private val moduleFixture = projectFixture.moduleFixture()

  @Test
  @Timeout(30)
  fun `removed source triggers auto-make`(): Unit = timeoutRunBlocking {
    assertRemovalTrigger("mod/src/Foo.java", expected = true)
  }

  @Test
  @Timeout(30)
  fun `removed source root triggers auto-make`(): Unit = timeoutRunBlocking {
    assertRemovalTrigger("mod/src", expected = true)
  }

  @Test
  @Timeout(30)
  fun `removed compiler output file does not trigger auto-make`(): Unit = timeoutRunBlocking {
    assertRemovalTrigger("mod/target/classes/Foo.class", expected = false)
  }

  @Test
  @Timeout(30)
  fun `removed compiler output root does not trigger auto-make`(): Unit = timeoutRunBlocking {
    assertRemovalTrigger("mod/target/classes", expected = false)
  }

  @Test
  @Timeout(30)
  fun `removed excluded root does not trigger auto-make`(): Unit = timeoutRunBlocking {
    // Maven clean removes the whole target directory.
    assertRemovalTrigger("mod/target", expected = false)
  }

  @Test
  @Timeout(30)
  fun `removed ignored file does not trigger auto-make`(): Unit = timeoutRunBlocking {
    assertRemovalTrigger("mod/.git/index", expected = false)
  }

  @Test
  @Timeout(30)
  fun `removed file outside of the project does not trigger auto-make`(): Unit = timeoutRunBlocking {
    assertRemovalTrigger("unrelated/notes.txt", expected = false)
  }

  @Test
  @Timeout(30)
  fun `created source triggers auto-make`(): Unit = timeoutRunBlocking {
    assertCreationTrigger("mod/src/New.java", expected = true)
  }

  @Test
  @Timeout(30)
  fun `created compiler output file does not trigger auto-make`(): Unit = timeoutRunBlocking {
    assertCreationTrigger("mod/target/classes/New.class", expected = false)
  }

  /**
   * Creates the file at [relativePath] and collects its events in [BulkFileListener.after].
   * The test classifies the events later in a read action, as the production listener does.
   */
  private suspend fun assertCreationTrigger(relativePath: String, expected: Boolean) {
    val project = createMavenLikeModule()
    val path = workspace.get().resolve(relativePath)
    // A VFS path always uses '/', while Path.toString() uses the operating system separator.
    val eventPath = path.invariantSeparatorsPathString
    val existing = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
    if (existing != null) {
      edtWriteAction { existing.delete(this@AutoMakeTriggerTest) }
    }
    val collected = mutableListOf<VFileEvent>()
    ApplicationManager.getApplication().messageBus.connect(disposable)
      .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
          collected.addAll(events.filter { it.path == eventPath })
        }
      })

    val parent = findFile(path.parent)
    edtWriteAction {
      if (relativePath.endsWith(".java") || relativePath.endsWith(".class")) {
        parent.createChildData(this@AutoMakeTriggerTest, path.fileName.toString())
      }
      else {
        parent.createChildDirectory(this@AutoMakeTriggerTest, path.fileName.toString())
      }
    }
    assertThat(collected).describedAs("VFS events about %s", relativePath).isNotEmpty()

    assertThat(readAction { BuildManager.shouldTriggerAutoMake(project, collected) })
      .describedAs("creation of %s", relativePath)
      .isEqualTo(expected)
  }

  /**
   * Deletes the file at [relativePath] and asks the trigger from inside [AsyncFileListener.prepareChange].
   * The production listener asks it in the same place.
   */
  private suspend fun assertRemovalTrigger(relativePath: String, expected: Boolean) {
    val project = createMavenLikeModule()
    val file = findFile(workspace.get().resolve(relativePath))
    val path = file.path
    val verdict = AtomicReference<Boolean>()
    VirtualFileManager.getInstance().addAsyncFileListener({ events ->
      val ownEvents = events.filter { it.path == path }
      if (ownEvents.isNotEmpty()) {
        verdict.set(BuildManager.shouldTriggerAutoMakeOnRemoval(project, ownEvents))
      }
      null
    }, disposable)

    edtWriteAction { file.delete(this@AutoMakeTriggerTest) }

    assertThat(verdict.get())
      .describedAs("removal of %s", relativePath)
      .isEqualTo(expected)
  }

  /**
   * A Maven-shaped module: sources and the compiler output share one content root, and `target` is excluded the way the
   * Maven importer excludes it.
   */
  private suspend fun createMavenLikeModule(): Project {
    val root = workspace.get()
    val source = createFile(root.resolve("mod/src/Foo.java"), "class Foo {}")
    val output = createFile(root.resolve("mod/target/classes/Foo.class"), "")
    createFile(root.resolve("mod/.git/index"), "")
    createFile(root.resolve("unrelated/notes.txt"), "")

    val module = moduleFixture.get()
    edtWriteAction {
      ModuleRootManager.getInstance(module).modifiableModel.apply {
        addContentEntry(findFile(root.resolve("mod"))).apply {
          addSourceFolder(source.parent, false)
          addExcludeFolder(findFile(root.resolve("mod/target")))
        }
        getModuleExtension(CompilerModuleExtension::class.java).apply {
          inheritCompilerOutputPath(false)
          setCompilerOutputPath(output.parent)
        }
        commit()
      }
    }

    val project = projectFixture.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    return project
  }

  private fun createFile(path: Path, text: String): VirtualFile {
    path.parent.createDirectories()
    path.writeText(text)
    return findFile(path)
  }

  private fun findFile(path: Path): VirtualFile =
    requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) { "not in VFS: $path" }
}
