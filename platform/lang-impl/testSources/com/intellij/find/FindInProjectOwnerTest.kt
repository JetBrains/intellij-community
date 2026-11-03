// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.find.impl.FindInProjectDirectoriesProvider
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.useProjectAsync
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.Processor
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Collections.synchronizedList

/**
 * Pins which project builds the PSI of a found file (see `FindInProjectTask.psiManagerFor`): the project of the search for a
 * file it holds, else the open project that holds the file, and the project of the search again for a file no project holds.
 * The roots are non-indexable, so the walk alone finds the files, and a custom scope lets the walk reach every root.
 */
@TestApplication
@RegistryKey("find.in.files.in.non.indexable.enable", "true")
class FindInProjectOwnerTest {
  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()
  private val baseDir get() = projectModel.baseProjectDir
  private val project get() = projectModel.project

  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun setup() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
  }

  @Test
  fun `a usage belongs to the open project that holds its file`(): Unit = runBlocking {
    val mine = root("mine")
    val theirs = root("theirs")
    val shared = root("shared")
    val nobody = root("nobody")
    addRoots(project, listOf(mine, shared))

    val otherDir = baseDir.newVirtualDirectory("other-project").toNioPath()
    checkNotNull(ProjectManagerEx.getInstanceEx().openProjectAsync(otherDir)).useProjectAsync { other ->
      addRoots(other, listOf(theirs, shared))
      registerProvider(listOf(mine, theirs, shared, nobody))

      val owners = findUsages(scopeOver(mine, theirs, shared, nobody)).associate { it.virtualFile!!.name to it.project }
      assertThat(owners).containsExactlyInAnyOrderEntriesOf(mapOf(
        "mine.txt" to project,
        // the other project holds the file, so its PSI builds there, and a usage opens there
        "theirs.txt" to other,
        // a file the project of the search holds stays with it, also when another project holds it too
        "shared.txt" to project,
        // a file no open project holds stays with the project of the search
        "nobody.txt" to project,
      ))
    }
  }

  @Test
  fun `an excluded file goes to the open project whose content holds it`(): Unit = runBlocking {
    val excluded = baseDir.newVirtualDirectory("excluded")
    val shared = root("excluded/shared")
    val own = root("excluded/own")
    // one model change adds the content root and its excluded folders, so the scan lists no excluded folder as content first
    ModuleRootModificationUtil.updateModel(projectModel.createModule()) { model ->
      val entry = model.addContentEntry(excluded)
      entry.addExcludeFolder(shared)
      entry.addExcludeFolder(own)
    }
    waitUntilIndexesAreReady(project)

    val otherDir = baseDir.newVirtualDirectory("other-project").toNioPath()
    checkNotNull(ProjectManagerEx.getInstanceEx().openProjectAsync(otherDir)).useProjectAsync { other ->
      addRoots(other, listOf(shared))
      registerProvider(listOf(shared, own))

      val owners = findUsages(scopeOver(shared, own)).associate { it.virtualFile!!.name to it.project }
      assertThat(owners).containsExactlyInAnyOrderEntriesOf(mapOf(
        // the other project holds the file as content, so the exclusion of the project of the search does not keep it
        "shared.txt" to other,
        // an excluded file no other project holds stays with the project of the search, which asked for the directory
        "own.txt" to project,
      ))
    }
  }

  /** A directory at [path] with one file, named after the directory, that holds the searched word. */
  private fun root(path: String): VirtualFile {
    val name = path.substringAfterLast('/')
    val root = baseDir.newVirtualDirectory(path)
    baseDir.newVirtualFile("$path/$name.txt", "$name file with zq data".toByteArray())
    return root
  }

  private suspend fun addRoots(project: Project, roots: List<VirtualFile>) {
    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    project.workspaceModel.update("add non-indexable roots") { storage ->
      for (root in roots) {
        storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
      }
    }
    VfsTestUtil.syncRefresh()
    waitUntilIndexesAreReady(project)
  }

  private fun registerProvider(directories: List<VirtualFile>) {
    val provider = object : FindInProjectDirectoriesProvider {
      override fun getProjectDirectories(project: Project): List<VirtualFile> = directories
    }
    FindInProjectDirectoriesProvider.EP_NAME.point.registerExtension(provider, disposable)
  }

  /** A search over a scope that spans [roots]. The pattern has no trigram, so no index candidates. */
  private fun scopeOver(vararg roots: VirtualFile): FindModel = FindModel().apply {
    stringToFind = "zq"
    isGlobal = true
    isMultipleFiles = true
    isProjectScope = false
    isWithSubdirectories = true
    isCustomScope = true
    customScope = object : GlobalSearchScope(project) {
      override fun contains(file: VirtualFile): Boolean = roots.any { VfsUtilCore.isAncestor(it, file, false) }
      override fun isSearchInModuleContent(aModule: Module): Boolean = true
      override fun isSearchInLibraries(): Boolean = false
    }
  }

  private fun findUsages(model: FindModel): List<UsageInfo> {
    val usages = synchronizedList<UsageInfo>(ArrayList())
    val consumer: Processor<UsageInfo> = CollectProcessor(usages)
    val presentation = FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model))
    FindInProjectUtil.findUsages(model, project, consumer, presentation)
    return usages.toList()
  }
}
