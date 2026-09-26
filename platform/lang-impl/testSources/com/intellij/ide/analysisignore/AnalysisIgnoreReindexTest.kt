// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.find.TextSearchService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.CommonProcessors
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val ENABLED = "ide.analysisignore.file.enabled"
private const val MARKER_TEXT = "analysisignore reindex marker"

@TestApplication
class AnalysisIgnoreReindexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file excluded from the first scan is indexed after the exclusion is removed`() = runBlocking {
    val projectRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot")
    val marker = projectModel.baseProjectDir.newVirtualFile("projectRoot/vet/Marker.txt", MARKER_TEXT.toByteArray())
    val excludeFile = projectModel.baseProjectDir.newVirtualFile("projectRoot/.analysisignore", "vet".toByteArray())

    // The first scan of this content finds the file and excludes `vet` before any file below it is indexed.
    PsiTestUtil.addSourceContentToRoots(projectModel.createModule(), projectRoot)
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertFalse(isInContent(marker))
    assertEquals(emptySet<VirtualFile>(), filesWithMarkerText())

    writeAction { excludeFile.delete(this) }
    AnalysisIgnoreService.getInstance(projectModel.project).processNow()
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)

    assertTrue(isInContent(marker))
    assertEquals(setOf(marker), filesWithMarkerText())
  }

  /** The layout of a Gradle or Maven project: the file is at the content root, and the excluded directory is inside a source root below. */
  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file in a source root excluded from the first scan is indexed after the exclusion is removed`() = runBlocking {
    val projectRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot")
    val sourceRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot/src/main/java")
    val marker = projectModel.baseProjectDir.newVirtualFile("projectRoot/src/main/java/vet/Marker.txt", MARKER_TEXT.toByteArray())
    val excludeFile = projectModel.baseProjectDir.newVirtualFile("projectRoot/.analysisignore", "vet".toByteArray())

    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, projectRoot)
    PsiTestUtil.addSourceRoot(module, sourceRoot)
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertFalse(isInContent(marker))
    assertEquals(emptySet<VirtualFile>(), filesWithMarkerText())

    writeAction { excludeFile.delete(this) }
    AnalysisIgnoreService.getInstance(projectModel.project).processNow()
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)

    assertTrue(isInContent(marker))
    assertEquals(setOf(marker), filesWithMarkerText())
  }

  /**
   * The layout of a Gradle project: one module holds the project root with the file, and a second module holds the source root
   * with the excluded directory as its own content root.
   */
  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file in a content root of another module excluded from the first scan is indexed after the exclusion is removed`() = runBlocking {
    val projectRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot")
    val sourceRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot/src/main/java")
    val marker = projectModel.baseProjectDir.newVirtualFile("projectRoot/src/main/java/vet/Marker.txt", MARKER_TEXT.toByteArray())
    val excludeFile = projectModel.baseProjectDir.newVirtualFile("projectRoot/.analysisignore", "vet".toByteArray())

    PsiTestUtil.addContentRoot(projectModel.createModule("root"), projectRoot)
    PsiTestUtil.addSourceContentToRoots(projectModel.createModule("main"), sourceRoot)
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertFalse(isInContent(marker))
    assertEquals(emptySet<VirtualFile>(), filesWithMarkerText())

    writeAction { excludeFile.delete(this) }
    AnalysisIgnoreService.getInstance(projectModel.project).processNow()
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)

    assertTrue(isInContent(marker))
    assertEquals(setOf(marker), filesWithMarkerText())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `a file indexed before the exclusion leaves the index and comes back`() = runBlocking {
    val projectRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot")
    val marker = projectModel.baseProjectDir.newVirtualFile("projectRoot/vet/Marker.txt", MARKER_TEXT.toByteArray())
    PsiTestUtil.addSourceContentToRoots(projectModel.createModule(), projectRoot)
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertEquals(setOf(marker), filesWithMarkerText())

    val excludeFile = projectModel.baseProjectDir.newVirtualFile("projectRoot/.analysisignore", "vet".toByteArray())
    AnalysisIgnoreService.getInstance(projectModel.project).processNow()
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertFalse(isInContent(marker))
    assertEquals(emptySet<VirtualFile>(), filesWithMarkerText())

    writeAction { excludeFile.delete(this) }
    AnalysisIgnoreService.getInstance(projectModel.project).processNow()
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertTrue(isInContent(marker))
    assertEquals(setOf(marker), filesWithMarkerText())
  }

  private suspend fun isInContent(file: VirtualFile): Boolean = readAction {
    WorkspaceFileIndex.getInstance(projectModel.project).isInContent(file)
  }

  /** The files of the project whose indexed content holds [MARKER_TEXT]. An unindexed file is absent here. */
  private suspend fun filesWithMarkerText(): Set<VirtualFile> = readAction {
    val processor = CommonProcessors.CollectProcessor<VirtualFile>()
    service<TextSearchService>().processFilesWithText(MARKER_TEXT, processor, GlobalSearchScope.projectScope(projectModel.project))
    processor.results.toSet()
  }
}
