// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.doNotEnableExternalStorageByDefaultInTests
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.createOrLoadProject
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.collectResults
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class RepositoryLibraryUtilsTest {
  companion object {
    @JvmField
    @ClassRule
    val applicationRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val projectDirectory = TemporaryDirectory()

  @JvmField
  @Rule
  val m2Directory = TemporaryDirectory()

  private val testRemoteRepositoriesRoot = getCommunityDirAbsolutePath("java/idea-ui/testData/sampleRepositories")

  private val baseProject = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectBase")
  private val projectWithBadSha256Checksum = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectBadChecksum")
  private val projectWithCorrectSha256Checksum = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectChecksumBuilt")
  private val projectWithCorrectGuessedRemoteRepositories = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectJarReposGuessed")
  private val projectWithAllPropertiesFilled = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectAllPropertiesFilled")

  @Before
  fun setUp() {
    val pathMacros = PathMacros.getInstance()
    pathMacros.setMacro(PathMacrosImpl.MAVEN_REPOSITORY, m2Directory.createDir().toString())

    /* See .idea/jarRepositories.xml in testData */
    pathMacros.setMacro("TEST_REMOTE_REPOSITORIES_ROOT", "file://$testRemoteRepositoriesRoot")
  }

  /**
   * Ensure internal [reloadAllRepositoryLibraries] test method works correctly
   */
  @Test
  fun `test reload all libraries reloads`() = testRepositoryLibraryUtils(baseProject) { project, _ ->
    val workspaceModel = WorkspaceModel.getInstance(project)
    val rootsBeforeReload = workspaceModel.currentSnapshot.entities(LibraryEntity::class.java)
      .map { it.roots }
      .flatten()
      .map { JpsPathUtil.urlToFile(it.url.url) }
      .toList()
    assertFalse("Bad BEFORE state: some roots already resolved", rootsBeforeReload.any { it.exists() })

    reloadAllRepositoryLibraries(project)

    val rootsAfterReload = workspaceModel.currentSnapshot.entities(LibraryEntity::class.java)
      .map { it.roots }
      .flatten()
      .map { JpsPathUtil.urlToFile(it.url.url) }
      .toList()
    assertTrue("Unexpected: not all roots resolved", rootsAfterReload.all { it.exists() })
  }

  @Test
  fun `test sha256 checksums not built on unresolved compile roots`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    utils.buildMissingSha256ChecksumsBackground().join()
    project.assertContentMatches(baseProject)
  }

  @Test
  fun `test sha256 checksums built correctly`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    reloadAllRepositoryLibraries(project) // reload libraries - checksum build requires compile roots resolved
    utils.buildMissingSha256ChecksumsBackground().join()
    project.assertContentMatches(projectWithCorrectSha256Checksum)
  }

  @Test
  fun `test bad checksums rebuild`() = testRepositoryLibraryUtils(projectWithBadSha256Checksum) { project, utils ->
    reloadAllRepositoryLibraries(project) // reload libraries - checksum build requires compile roots resolved
    utils.buildMissingSha256ChecksumsBackground().join()
    project.assertContentMatches(projectWithBadSha256Checksum) // Ensure build checksums left untouched
    utils.rebuildExistingSha256ChecksumsBackground().join()
    project.assertContentMatches(projectWithCorrectSha256Checksum) // Now ensure they're rebuilt
  }

  @Test
  fun `test checksums disable`() = testRepositoryLibraryUtils(projectWithCorrectSha256Checksum) { project, utils ->
    utils.removeSha256ChecksumsBackground().join()
    project.assertContentMatches(baseProject)
  }

  @Test
  fun `test bind repositories guessed correctly`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    utils.guessAndBindRemoteRepositoriesModal().join()
    project.assertContentMatches(projectWithCorrectGuessedRemoteRepositories)
  }

  @Test
  fun `test unbind remote repositories`() = testRepositoryLibraryUtils(projectWithCorrectGuessedRemoteRepositories) { project, utils ->
    utils.unbindRemoteRepositoriesModal().join()
    project.assertContentMatches(baseProject)
  }

  @Test
  fun `test check all libs can be resolved`() = testRepositoryLibraryUtils(projectWithCorrectGuessedRemoteRepositories) { project, utils ->
    assertTrue(utils.checkAllLibrariesCanBeResolvedBackground().await())
    val remoteRepoConfig = RemoteRepositoriesConfiguration.getInstance(project)
    remoteRepoConfig.repositories = remoteRepoConfig.repositories.toMutableList().apply { removeAt(0) }
    assertFalse(utils.checkAllLibrariesCanBeResolvedBackground().await())
  }

  @Test
  fun `test compute properties for new library builds checksums`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    reloadAllRepositoryLibraries(project)
    val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
    utils.computePropertiesForLibraries(snapshot.entities(LibraryEntity::class.java).toSet(),
                                        buildSha256Checksum = true, guessAndBindRemoteRepository = false)!!.join()
    project.assertContentMatches(projectWithCorrectSha256Checksum)
  }

  @Test
  fun `test compute properties for new library rebuilds checksums`() = testRepositoryLibraryUtils(
    projectWithBadSha256Checksum) { project, utils ->
    reloadAllRepositoryLibraries(project)
    val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
    utils.computePropertiesForLibraries(snapshot.entities(LibraryEntity::class.java).toSet(),
                                        buildSha256Checksum = true, guessAndBindRemoteRepository = false)!!.join()
    project.assertContentMatches(projectWithCorrectSha256Checksum)
  }

  @Test
  fun `test compute properties for new library guesses repositories`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    reloadAllRepositoryLibraries(project)
    val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
    utils.computePropertiesForLibraries(snapshot.entities(LibraryEntity::class.java).toSet(),
                                        buildSha256Checksum = false, guessAndBindRemoteRepository = true)!!.join()
    project.assertContentMatches(projectWithCorrectGuessedRemoteRepositories)
  }

  @Test
  fun `test compute properties for new library fills all properties`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    reloadAllRepositoryLibraries(project)
    val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
    utils.computePropertiesForLibraries(snapshot.entities(LibraryEntity::class.java).toSet(),
                                        buildSha256Checksum = true, guessAndBindRemoteRepository = true)!!.join()
    project.assertContentMatches(projectWithAllPropertiesFilled)
  }

  private fun getCommunityDirAbsolutePath(relative: String) = PathManagerEx.findFileUnderCommunityHome(relative).absolutePath.toNioPath()

  private fun testRepositoryLibraryUtils(sampleProjectPath: Path, checkProject: suspend (Project, RepositoryLibraryUtils) -> Unit) {
    fun copyProjectFiles(dir: VirtualFile): Path {
      val projectDir = dir.toNioPath()
      FileUtil.copyDir(sampleProjectPath.toFile(), projectDir.toFile())
      VfsUtil.markDirtyAndRefresh(false, true, true, dir)
      return projectDir
    }

    doNotEnableExternalStorageByDefaultInTests {
      runBlocking {
        createOrLoadProject(projectDirectory, ::copyProjectFiles, loadComponentState = true, useDefaultProjectSettings = false) { project ->
          val utils = RepositoryLibraryUtils.getInstance(project)
          utils.setTestCoroutineScope(this)
          checkProject(project, utils)
          utils.resetTestCoroutineScope()
        }
      }
    }
  }

  private suspend fun Project.assertContentMatches(path: Path) {
    stateStore.save()
    path.assertMatches(directoryContentOf(this.basePath!!.toNioPath()))
  }

  private suspend fun reloadAllRepositoryLibraries(project: Project) = coroutineScope {
    RepositoryLibrarySynchronizer.collectLibraries(project) {
      (it as? LibraryEx)?.properties is RepositoryLibraryProperties
    }
      .filterIsInstance<LibraryEx>()
      .map { lib -> RepositoryUtils.reloadDependencies(project, lib) }
      .collectResults().await()
  }
}
