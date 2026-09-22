// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.DirectorySearchEngine
import com.intellij.find.FindInProjectSearchEngine
import com.intellij.find.FindModel
import com.intellij.find.FindModelExtension
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.usages.FindUsagesProcessPresentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

@TestApplication
@Timeout(30)
internal class DirectorySearchEngineTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project by projectFixture
  private val directoryPath by tempPathFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun disableOtherSearchers() {
    ExtensionTestUtil.maskExtensions(FindInProjectSearchEngine.EP_NAME, emptyList(), disposable)
    ExtensionTestUtil.maskExtensions(FindModelExtension.EP_NAME, emptyList(), disposable)
  }

  @Test
  fun `the default engine expands one level at a time`(): Unit = timeoutRunBlocking {
    val root = createFiles("root.txt", "child/child.txt", "child/deep/deep.txt")
    val visited = ConcurrentLinkedQueue<String>()
    addEngine(weight = {
      visited.add(it.path)
      -1
    }) { _, _ -> error("The engine cannot handle this directory") }

    assertThat(search(root)).containsExactlyInAnyOrder("root.txt", "child/child.txt", "child/deep/deep.txt")
    assertThat(visited).containsExactlyInAnyOrder(root.path, "${root.path}/child", "${root.path}/child/deep")
  }

  @Test
  fun `the highest weight selects an engine for each directory`(): Unit = timeoutRunBlocking {
    val root = createFiles("root.txt", "child/child.txt")
    val expanded = ConcurrentLinkedQueue<String>()
    addEngine(weight = { if (it.path == root.path) 10 else 1 }) { directory, consumer ->
      expanded.add("first:${directory.path}")
      consumer.accept(directory.children.asList())
    }
    addEngine(weight = { if (it.path == root.path) 2 else 20 }) { directory, consumer ->
      expanded.add("second:${directory.path}")
      consumer.accept(directory.children.asList())
    }

    assertThat(search(root)).containsExactlyInAnyOrder("root.txt", "child/child.txt")
    assertThat(expanded).containsExactlyInAnyOrder("first:${root.path}", "second:${root.path}/child")
  }

  @Test
  fun `an engine can expand several levels and defer a directory`(): Unit = timeoutRunBlocking {
    val root = createFiles("child/deep/first.txt", "child/deep/skip.log", "child/deep/rest/last.txt")
    addEngine(weight = { if (it.path == root.path) 10 else -1 }) { directory, consumer ->
      consumer.accept(listOf(
        requireNotNull(directory.findFileByRelativePath("child/deep/first.txt")),
        requireNotNull(directory.findFileByRelativePath("child/deep/skip.log")),
      ))
      consumer.accept(listOf(requireNotNull(directory.findFileByRelativePath("child/deep/rest"))))
    }

    assertThat(search(root, fileMask = "*.txt")).containsExactlyInAnyOrder("child/deep/first.txt", "child/deep/rest/last.txt")
  }

  @Test
  fun `equal weights select one engine`(): Unit = timeoutRunBlocking {
    val root = createFiles("root.txt")
    val selected = ConcurrentLinkedQueue<Int>()
    for (id in 1..2) {
      addEngine(weight = { 10 }) { directory, consumer ->
        selected.add(id)
        consumer.accept(directory.children.asList())
      }
    }

    assertThat(search(root)).containsExactly("root.txt")
    assertThat(selected).hasSize(1)
  }

  @Test
  fun `a search without subdirectories does not expand directories`(): Unit = timeoutRunBlocking {
    val root = createFiles("root.txt", "child/child.txt")
    addEngine(weight = { error("Directory expansion is disabled") }) { _, _ -> error("Directory expansion is disabled") }

    assertThat(search(root, withSubdirectories = false)).containsExactly("root.txt")
  }

  @Test
  fun `an incompatible engine is filtered once before directory expansion`(): Unit = timeoutRunBlocking {
    val root = createFiles("root.txt", "child/child.txt")
    val checks = AtomicInteger()
    addEngine(
      canSearch = {
        checks.incrementAndGet()
        false
      },
      weight = { error("The engine cannot handle this request") },
    ) { _, _ -> error("The engine cannot handle this request") }

    assertThat(search(root)).containsExactlyInAnyOrder("root.txt", "child/child.txt")
    assertThat(checks.get()).isEqualTo(1)
  }

  @Test
  fun `unsaved documents are searched even when the directory engine returns no files`(): Unit = timeoutRunBlocking {
    val root = createFiles("changed.txt")
    val file = requireNotNull(root.findChild("changed.txt"))
    edtWriteAction {
      VfsUtil.saveText(file, "before")
      requireNotNull(FileDocumentManager.getInstance().getDocument(file)).setText("needle")
    }
    val searchedDirectories = ConcurrentLinkedQueue<String>()
    addEngine(weight = { 1 }) { directory, _ ->
      searchedDirectories.add(directory.path)
    }

    assertThat(search(root)).containsExactly("changed.txt")
    assertThat(searchedDirectories).containsExactly(root.path)
  }

  private suspend fun createFiles(vararg paths: String): VirtualFile = edtWriteAction {
    val root = VfsUtil.createDirectories(directoryPath.toString())
    for (path in paths) {
      val parent = VfsUtil.createDirectories("${root.path}/${path.substringBeforeLast('/', "")}")
      VfsUtil.saveText(parent.createChildData(this, path.substringAfterLast('/')), "needle")
    }
    root
  }

  private fun addEngine(
    canSearch: (FindModel) -> Boolean = { true },
    weight: (VirtualFile) -> Int,
    expand: (VirtualFile, Consumer<in Collection<VirtualFile>>) -> Unit,
  ) {
    val engine = object : DirectorySearchEngine {
      override fun canSearch(findModel: FindModel): Boolean = canSearch(findModel)

      override fun getWeight(directory: VirtualFile): Int = weight(directory)

      override fun searchDirectory(directory: VirtualFile, findModel: FindModel, consumer: Consumer<in Collection<VirtualFile>>) {
        expand(directory, consumer)
      }
    }
    DirectorySearchEngine.EP_NAME.point.registerExtension(engine, disposable)
  }

  private fun search(root: VirtualFile, withSubdirectories: Boolean = true, fileMask: String? = null): Collection<String> {
    val model = FindModel().apply {
      stringToFind = "needle"
      isProjectScope = false
      isMultipleFiles = true
      directoryName = root.path
      isWithSubdirectories = withSubdirectories
      fileFilter = fileMask
    }
    val results = ConcurrentLinkedQueue<String>()
    val presentation = FindUsagesProcessPresentation(FindInProjectUtil.setupViewPresentation(true, model))
    FindInProjectUtil.findUsages(model, project, { usage ->
      results.add(requireNotNull(VfsUtil.getRelativePath(requireNotNull(usage.virtualFile), root)))
      true
    }, presentation)
    return results
  }
}
