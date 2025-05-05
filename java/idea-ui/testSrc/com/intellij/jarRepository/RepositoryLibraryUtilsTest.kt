// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.doNotEnableExternalStorageByDefaultInTests
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.project.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.nio.file.Path

class RepositoryLibraryUtilsTest {
  companion object {
    /**
     * See .idea/libraries/ in testData
     */
    var mavenRepositoryOld: String? = null
    /**
     * See .idea/jarRepositories.xml in testData
     */
    private const val TEST_REMOTE_REPOSITORIES_ROOT_MACRO = "REPOSITORY_LIBRARY_UTILS_TEST_REMOTE_REPOSITORIES_ROOT"

    @JvmField
    @ClassRule
    val applicationRule = ApplicationRule()

    @JvmField
    @ClassRule
    val m2Directory = TemporaryDirectory()

    private val m2DirectoryPath by lazy { m2Directory.createDir() }

    @BeforeClass
    @JvmStatic
    fun beforeAll() {
      val pathMacros: PathMacros = PathMacros.getInstance()
      mavenRepositoryOld = pathMacros.getValue("MAVEN_REPOSITORY")
      pathMacros.setMacro("MAVEN_REPOSITORY", m2DirectoryPath.toString())
    }

    @AfterClass
    @JvmStatic
    fun afterAll() {
      val pathMacros: PathMacros = PathMacros.getInstance()
      pathMacros.setMacro("MAVEN_REPOSITORY", mavenRepositoryOld)
    }
  }

  @JvmField
  @Rule
  val projectDirectory = TemporaryDirectory()


  private val testRemoteRepositoriesRoot = getCommunityDirAbsolutePath("java/idea-ui/testData/sampleRepositories")

  private val baseProject = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectBase")
  private val projectWithBadSha256Checksum = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectBadChecksum")
  private val projectWithCorrectSha256Checksum = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectChecksumBuilt")
  private val projectWithCorrectGuessedRemoteRepositories = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectJarReposGuessed")
  private val projectWithAllPropertiesFilled = getCommunityDirAbsolutePath("java/idea-ui/testData/testProjectAllPropertiesFilled")

  @Before
  fun setUp() {
    /* clear test .m2 before each */
    m2DirectoryPath.deleteRecursively()
    m2DirectoryPath.createDirectory()

    val pathMacros: PathMacros = PathMacros.getInstance()
    pathMacros.setMacro(TEST_REMOTE_REPOSITORIES_ROOT_MACRO, "file://$testRemoteRepositoriesRoot")
  }

  @Test
  fun `test sha256 checksums built on unresolved compile roots`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    utils.buildMissingSha256ChecksumsBackground().join()
    project.assertContentMatches(projectWithCorrectSha256Checksum)
  }

  @Test
  fun `test sha256 checksums built correctly`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    utils.buildMissingSha256ChecksumsBackground().join()
    project.assertContentMatches(projectWithCorrectSha256Checksum)
  }

  @Test
  fun `test bad checksums rebuild`() = testRepositoryLibraryUtils(projectWithBadSha256Checksum) { project, utils ->
    utils.buildMissingSha256ChecksumsBackground().join()
    project.assertContentMatches(projectWithBadSha256Checksum) // Ensure build checksums left untouched
    utils.removeSha256ChecksumsBackground().join()
    utils.buildMissingSha256ChecksumsBackground().join()
    project.assertContentMatches(projectWithCorrectSha256Checksum) // Now ensure they're rebuilt
  }

  @Test
  fun `test checksums disable`() = testRepositoryLibraryUtils(projectWithCorrectSha256Checksum) { project, utils ->
    utils.removeSha256ChecksumsBackground().join()
    project.assertContentMatches(baseProject)
  }

  @Test
  fun `test bind repositories guessed correctly`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    utils.guessAndBindRemoteRepositoriesBackground().join()
    project.assertContentMatches(projectWithCorrectGuessedRemoteRepositories)
  }

  @Test
  fun `test unbind remote repositories`() = testRepositoryLibraryUtils(projectWithCorrectGuessedRemoteRepositories) { project, utils ->
    utils.unbindRemoteRepositoriesBackground().join()
    project.assertContentMatches(baseProject)
  }

  @Test
  fun `test check all libs can be resolved`() = testRepositoryLibraryUtils(projectWithCorrectGuessedRemoteRepositories) { project, utils ->
    assertTrue(utils.resolveAllBackground().await())
    val remoteRepoConfig = RemoteRepositoriesConfiguration.getInstance(project)
    remoteRepoConfig.repositories = remoteRepoConfig.repositories.toMutableList().apply { removeAt(0) }
    assertFalse(utils.resolveAllBackground().await())
  }

  @Test
  fun `test compute properties for new library builds checksums`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
    utils.computeExtendedPropertiesFor(snapshot.entities(LibraryEntity::class.java).toSet(),
                                       buildSha256Checksum = true, guessAndBindRemoteRepository = false)!!.join()
    project.assertContentMatches(projectWithCorrectSha256Checksum)
  }

  @Test
  fun `test compute properties for new library does not rebuild checksums`() =
    testRepositoryLibraryUtils(projectWithBadSha256Checksum) { project, utils ->
      val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
      utils.computeExtendedPropertiesFor(snapshot.entities(LibraryEntity::class.java).toSet(),
                                         buildSha256Checksum = true, guessAndBindRemoteRepository = false)!!.join()
      project.assertContentMatches(projectWithBadSha256Checksum)
    }

  @Test
  fun `test compute properties for new library guesses repositories`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
    utils.computeExtendedPropertiesFor(snapshot.entities(LibraryEntity::class.java).toSet(),
                                       buildSha256Checksum = false, guessAndBindRemoteRepository = true)!!.join()
    project.assertContentMatches(projectWithCorrectGuessedRemoteRepositories)
  }

  @Test
  fun `test compute properties for new library fills all properties`() = testRepositoryLibraryUtils(baseProject) { project, utils ->
    val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
    utils.computeExtendedPropertiesFor(snapshot.entities(LibraryEntity::class.java).toSet(),
                                       buildSha256Checksum = true, guessAndBindRemoteRepository = true)!!.join()
    project.assertContentMatches(projectWithAllPropertiesFilled)
  }

  private fun getCommunityDirAbsolutePath(relative: String) = Path.of(PathManagerEx.findFileUnderCommunityHome(relative).absolutePath)

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
    path.assertMatches(directoryContentOf(Path.of(this.basePath!!)))
  }
}
