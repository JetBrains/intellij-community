// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.jarRepository.JarHttpDownloaderTestUtil.TestHttpServerExtension
import com.intellij.jarRepository.JarHttpDownloaderTestUtil.createContext
import com.intellij.jarRepository.JarHttpDownloaderTestUtil.url
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.ex.PathManagerEx.findFileUnderCommunityHome
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.doNotEnableExternalStorageByDefaultInTests
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TemporaryDirectoryExtension
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.runBlocking
import org.jetbrains.concurrency.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@TestApplication
class JarHttpDownloaderJpsTest {
  private val TEST_MAVEN_LOCAL_REPOSITORY_MACRO = "REPOSITORY_LIBRARY_UTILS_TEST_LOCAL_MAVEN_REPOSITORY"
  private val TEST_REMOTE_REPOSITORIES_ROOT_MACRO = "REPOSITORY_LIBRARY_UTILS_TEST_REMOTE_REPOSITORIES_ROOT"

  @JvmField
  @RegisterExtension
  val m2DirectoryExtension = TemporaryDirectoryExtension()

  private val m2DirectoryPath by lazy { m2DirectoryExtension.createDir() }

  private val authUsername = "user"

  @Suppress("SpellCheckingInspection")
  private val authPassword = "passw0rd"


  @RegisterExtension
  @JvmField
  internal val serverExtension = TestHttpServerExtension { server ->
    server.application.createContext("/", HttpStatusCode.NotFound)

    server.application.createContext(
      path = "/org/apache/commons/commons-math3/3.6/commons-math3-3.6-sources.jar",
      httpStatusCode = HttpStatusCode.OK,
      response = "fake sources jar content",
      auth = JarRepositoryAuthenticationDataProvider.AuthenticationData(authUsername, authPassword),
    )

    server.application.createContext(
      path = "/org/apache/commons/commons-math3/3.6/commons-math3-3.6.jar",
      httpStatusCode = HttpStatusCode.OK,
      response = "fake jar content",
      auth = JarRepositoryAuthenticationDataProvider.AuthenticationData(authUsername, authPassword),
    )
  }
  private val server: ApplicationEngine get() = serverExtension.server.engine

  private val disposable = Disposer.newDisposable(javaClass.name)

  @BeforeEach
  fun beforeEach() {
    val pathMacros: PathMacros = PathMacros.getInstance()
    pathMacros.setMacro(TEST_MAVEN_LOCAL_REPOSITORY_MACRO, m2DirectoryPath.toString())
    Disposer.register(disposable) {
      pathMacros.setMacro(TEST_MAVEN_LOCAL_REPOSITORY_MACRO, null)
    }
    pathMacros.setMacro(TEST_REMOTE_REPOSITORIES_ROOT_MACRO, server.url)
    Disposer.register(disposable) {
      pathMacros.setMacro(TEST_REMOTE_REPOSITORIES_ROOT_MACRO, null)
    }
    JarRepositoryManager.setLocalRepositoryPath(m2DirectoryPath.toFile())
    Disposer.register(disposable) {
      JarRepositoryManager.setLocalRepositoryPath(null)
    }

    val repo = PathMacros.getInstance().getValue("MAVEN_REPOSITORY")
    PathMacros.getInstance().setMacro("MAVEN_REPOSITORY", m2DirectoryPath.toString())

    Disposer.register(disposable) {
      PathMacros.getInstance().setMacro("MAVEN_REPOSITORY", repo)
    }

    assertTrue(JarHttpDownloader.forceHttps, "default forceHttps must be true")
    JarHttpDownloader.forceHttps = false
    Disposer.register(disposable) {
      JarHttpDownloader.forceHttps = true
    }

    JarRepositoryAuthenticationDataProvider.KEY.point.registerExtension(object : JarRepositoryAuthenticationDataProvider {
      override fun provideAuthenticationData(description: RemoteRepositoryDescription): JarRepositoryAuthenticationDataProvider.AuthenticationData? = when (description.url) {
        server.url -> JarRepositoryAuthenticationDataProvider.AuthenticationData(authUsername, authPassword)
        else -> null
      }

    }, disposable)
  }

  @AfterEach
  fun afterEach() {
    Disposer.dispose(disposable)
  }

  @RegisterExtension
  @JvmField
  val projectDirectory = TemporaryDirectoryExtension()

  @Test
  fun happy_case() = testRepositoryLibraryUtils(projectTestData) { project, utils ->

    val libraryRelease = getLibrary(project, "apache.commons.math3") as LibraryEx
    val promise = JarHttpDownloaderJps.getInstance(project).downloadLibraryFilesAsync(libraryRelease)
    promise!!.await()

    val jar = m2DirectoryPath.resolve("org/apache/commons/commons-math3/3.6/commons-math3-3.6.jar")
    assertEquals("fake jar content", jar.readText())

    val sourcesJar = m2DirectoryPath.resolve("org/apache/commons/commons-math3/3.6/commons-math3-3.6-sources.jar")
    assertEquals("fake sources jar content", sourcesJar.readText())

  }

  @Test
  fun bad_checksum() = testRepositoryLibraryUtils(projectTestData) { project, utils ->
    val libraryRelease = getLibrary(project, "apache.commons.math3.bad.checksum") as LibraryEx
    val promise = JarHttpDownloaderJps.getInstance(project).downloadLibraryFilesAsync(libraryRelease)

    val exception = assertFailsWith<IllegalStateException> {
      promise.await()
    }

    assertTrue(exception.message!!.startsWith("Failed to download 1 artifact(s): (first exception) Wrong file checksum"), exception.message!!)

    val cause = exception.cause!!
    assertTrue(cause.message!!.contains("Wrong file checksum after downloading '${server.url}/org/apache/commons/commons-math3/3.6/commons-math3-3.6.jar'"), cause.message!!)
    assertTrue(cause.message!!.contains("expected checksum 000000000000000000000000000000000000000000000000000000000000028a, but got 79b0baf88d2bc643f652f413e52702d81ac40a9b782d7f00fc431739e8d1c28a"), cause.message!!)

    // not downloaded due to wrong checksum
    val jar = m2DirectoryPath.resolve("org/apache/commons/commons-math3/3.6/commons-math3-3.6.jar")
    Assertions.assertFalse(jar.exists())

    // still downloaded
    val sourcesJar = m2DirectoryPath.resolve("org/apache/commons/commons-math3/3.6/commons-math3-3.6-sources.jar")
    assertEquals("fake sources jar content", sourcesJar.readText())

  }

  private suspend fun Project.withMavenRepoReplace(f: suspend () -> Unit) {
    val serviceDisposable = Disposer.newDisposable(disposable)
    val oldService = PathMacroManager.getInstance(this)
    replaceService(PathMacroManager::class.java, object : PathMacroManager(null) {
      override fun expandPath(text: String?): String? {
        if (text == JarRepositoryManager.MAVEN_REPOSITORY_MACRO) return m2DirectoryPath.absolutePathString()
        return oldService.expandPath(text)
      }
    }, disposable);
    try {
      f()
    }
    finally {
      Disposer.dispose(serviceDisposable)
    }
  }

  private fun getLibrary(project: Project, name: String) =
    LibraryTablesRegistrar.getInstance()
      .getLibraryTable(project)
      .getLibraryByName(name)
    ?: error("Library '$name' was not found in ${project.basePath}")

  private val projectTestData = findFileUnderCommunityHome("java/idea-ui/testData/testProjectJarHttpDownloader").toPath()

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
          project.withMavenRepoReplace {
            val utils = RepositoryLibraryUtils.getInstance(project)
            utils.setTestCoroutineScope(this)
            checkProject(project, utils)
            utils.resetTestCoroutineScope()
          }
        }
      }
    }
  }

}