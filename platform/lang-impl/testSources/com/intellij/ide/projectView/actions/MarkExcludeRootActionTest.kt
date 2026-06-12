// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.OptionalExclusionContributor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class MarkExcludeRootActionTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  private lateinit var module: Module
  private lateinit var contentRoot: VirtualFile

  @Before
  fun setUp() {
    module = projectModel.createModule()
    contentRoot = projectModel.baseProjectDir.newVirtualDirectory("module/content")
    ModuleRootModificationUtil.addContentRoot(module, contentRoot)
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
  }

  @Test
  fun `mark exclude is enabled for a file inside a content root`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/note.txt")
    assertEnabled(MarkExcludeRootAction(), file)
  }

  @Test
  fun `mark exclude is enabled for a mixed file plus directory selection`() {
    val dir = projectModel.baseProjectDir.newVirtualDirectory("module/content/sub")
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/note.txt")
    assertEnabled(MarkExcludeRootAction(), dir, file)
  }

  @Test
  fun `mark exclude is disabled for a file inside an already-excluded directory`() {
    val excluded = projectModel.baseProjectDir.newVirtualDirectory("module/content/excluded")
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/excluded/inside.txt")
    excludeFolderOnContentRoot(excluded)
    assertDisabled(MarkExcludeRootAction(), file)
  }

  @Test
  fun `excluding a file marks it as excluded`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/generated.bin")
    val fileIndex = ProjectFileIndex.getInstance(projectModel.project)
    assertThat(runReadActionBlocking { fileIndex.isExcluded(file) }).isFalse()

    invoke(MarkExcludeRootAction(), file)

    assertThat(runReadActionBlocking { fileIndex.isExcluded(file) }).isTrue()
  }

  @Test
  fun `unmark restores a file that was excluded via the action`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/generated.bin")
    val fileIndex = ProjectFileIndex.getInstance(projectModel.project)

    invoke(MarkExcludeRootAction(), file)
    assertThat(runReadActionBlocking { fileIndex.isExcluded(file) }).isTrue()

    assertEnabled(UnmarkRootAction(), file)
    invoke(UnmarkRootAction(), file)

    assertThat(runReadActionBlocking { fileIndex.isExcluded(file) }).isFalse()
    assertThat(runReadActionBlocking { fileIndex.isInContent(file) }).isTrue()
  }

  @Test
  fun `actions that do not accept files stay disabled when a file is in the selection`() {
    val dir = projectModel.baseProjectDir.newVirtualDirectory("module/content/sub")
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/note.txt")

    val directoryOnlyAction = object : MarkRootActionBase() {
      override fun modifyRoots(file: VirtualFile, entry: ContentEntry) {}
      override fun isEnabled(selection: RootsSelection, module: Module): Boolean = true
    }

    assertEnabled(directoryOnlyAction, dir)
    assertDisabled(directoryOnlyAction, file)
    assertDisabled(directoryOnlyAction, dir, file)
  }

  @Test
  fun `unmark is enabled for a file that an exclusion contributor can cancel`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/contributed.txt")
    maskExclusionContributors(canCancel = true)
    assertEnabled(UnmarkRootAction(), file)
  }

  @Test
  fun `unmark is disabled for a file that no exclusion contributor can cancel`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/contributed.txt")
    maskExclusionContributors(canCancel = false)
    assertDisabled(UnmarkRootAction(), file)
  }

  private fun maskExclusionContributors(canCancel: Boolean) {
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName<OptionalExclusionContributor>("com.intellij.workspaceModel.optionalExclusionContributor"),
      listOf(FakeExclusionContributor(canCancel)),
      disposableRule.disposable,
    )
  }

  @Test
  fun `MarkRootGroup switches to Mark File As when selection contains only files`() {
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/note.txt")
    assertThat(isFilesOnlySelection(arrayOf(file))).isTrue()
  }

  @Test
  fun `MarkRootGroup treats a selection containing a directory as not files-only`() {
    val dir = projectModel.baseProjectDir.newVirtualDirectory("module/content/sub")
    val file = projectModel.baseProjectDir.newVirtualFile("module/content/note.txt")
    assertThat(isFilesOnlySelection(arrayOf(dir))).isFalse()
    assertThat(isFilesOnlySelection(arrayOf(dir, file))).isFalse()
  }

  private fun isFilesOnlySelection(files: Array<VirtualFile>): Boolean {
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, projectModel.project)
      .add(PlatformCoreDataKeys.MODULE, module)
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files)
      .build()
    val event = AnActionEvent.createEvent(MarkRootGroup(), context, Presentation(), "", ActionUiKind.NONE, null)
    return MarkRootGroup.isFilesOnlySelection(event)
  }

  private fun excludeFolderOnContentRoot(file: VirtualFile) {
    ModuleRootModificationUtil.updateModel(module) { model ->
      val entry = model.contentEntries.first { contentEntry ->
        val root = contentEntry.file
        root != null && VfsUtilCore.isAncestor(root, file, false)
      }
      entry.addExcludeFolder(file)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
  }

  private fun invoke(action: AnAction, vararg files: VirtualFile) {
    ApplicationManager.getApplication().invokeAndWait {
      action.actionPerformed(buildEvent(action, files))
    }
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
  }

  private fun assertEnabled(action: AnAction, vararg files: VirtualFile) {
    assertThat(updatePresentation(action, files).isEnabled)
      .withFailMessage { "Expected ${action::class.simpleName} to be enabled for ${files.map { it.name }}" }
      .isTrue()
  }

  private fun assertDisabled(action: AnAction, vararg files: VirtualFile) {
    assertThat(updatePresentation(action, files).isEnabled)
      .withFailMessage { "Expected ${action::class.simpleName} to be disabled for ${files.map { it.name }}" }
      .isFalse()
  }

  private fun updatePresentation(action: AnAction, files: Array<out VirtualFile>): Presentation {
    val event = buildEvent(action, files)
    runReadActionBlocking { action.update(event) }
    return event.presentation
  }

  private fun buildEvent(action: AnAction, files: Array<out VirtualFile>): AnActionEvent {
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, projectModel.project)
      .add(PlatformCoreDataKeys.MODULE, module)
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files)
      .build()
    return AnActionEvent.createEvent(action, context, Presentation(), "", ActionUiKind.NONE, null)
  }

  private class FakeExclusionContributor(private val canCancel: Boolean) : OptionalExclusionContributor {
    override fun requestExclusion(project: Project, fileOrDir: VirtualFile): Boolean = false
    override fun canCancelExclusion(project: Project, excludedFileOrDir: VirtualFile): Boolean = canCancel
    override fun requestExclusionCancellation(project: Project, excludedFileOrDir: VirtualFile): Boolean = true
  }
}
