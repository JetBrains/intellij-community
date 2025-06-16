// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.java.library.getMavenCoordinates
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestApplication
class RepositoryLibraryTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @JvmField
  @RegisterExtension
  val projectRule: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val mavenRepo = TempDirectoryExtension()

  @JvmField
  @RegisterExtension
  val localMavenCache = TempDirectoryExtension()

  private val ARTIFACT_NAME = "myArtifact"
  private val GROUP_NAME = "myGroup"
  private val LIBRARY_NAME = "NewLibrary"

  @BeforeEach
  fun setUp() {

    val repo = PathMacros.getInstance().getValue("MAVEN_REPOSITORY")
    PathMacros.getInstance().setMacro("MAVEN_REPOSITORY", localMavenCache.root.absolutePath)
    Disposer.register(disposable) {
      PathMacros.getInstance().setMacro("MAVEN_REPOSITORY", repo)
    }

    val oldService = PathMacroManager.getInstance(projectRule.project)
    projectRule.project.replaceService(PathMacroManager::class.java, object : PathMacroManager(null) {
      override fun expandPath(text: String?): String? {
        if (text == JarRepositoryManager.MAVEN_REPOSITORY_MACRO) return localMavenCache.root.absolutePath
        return oldService.expandPath(text)
      }
    }, disposable);

    MavenRepoFixture(mavenRepo.root).apply {
      addLibraryArtifact(group = GROUP_NAME, artifact = ARTIFACT_NAME, version = "1.0")
      addLibraryArtifact(group = GROUP_NAME, artifact = ARTIFACT_NAME, version = "1.0-SNAPSHOT")
      generateMavenMetadata(GROUP_NAME, ARTIFACT_NAME)
    }

    RemoteRepositoriesConfiguration.getInstance(projectRule.project).repositories = listOf(
      RemoteRepositoryDescription("id", "name", mavenRepo.root.toURI().toURL().toString())
    )
  }

  private fun createLibrary(version: String = "1.0", libraryName: String = LIBRARY_NAME, block: (LibraryEx.ModifiableModelEx) -> Unit = {}): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(projectRule.project)
    val library = runWriteActionAndWait {
      projectRule.addProjectLevelLibrary(libraryName) {
        it.kind = RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
        it.properties = RepositoryLibraryProperties(GROUP_NAME, ARTIFACT_NAME, version, false, emptyList())
        block(it)
      }
    }
    Disposer.register(disposable) {
      runWriteActionAndWait {
        if (!library.isDisposed) {
          libraryTable.removeLibrary(library)
        }
      }
    }
    return library
  }

  private fun getLibraryRoots(library: Library): Set<Pair<OrderRootType, String>> =
    OrderRootType.getAllTypes().flatMap { rootType ->
      library.getUrls(rootType).map { url -> rootType to url }
    }.toSet()

  @Test
  fun libraryResolveWithoutRoots() {
    val jar = localMavenCache.rootPath.resolve(GROUP_NAME).resolve(ARTIFACT_NAME).resolve("1.0").resolve("$ARTIFACT_NAME-1.0.jar")
    assertFalse(jar.exists())

    val library = createLibrary()
    assertEquals(0, getLibraryRoots(library).size)
    val mavenCoordinates = library.getMavenCoordinates()
    assertNotNull(mavenCoordinates)
    assertEquals(GROUP_NAME, mavenCoordinates!!.groupId)
    assertEquals(ARTIFACT_NAME, mavenCoordinates.artifactId)
    assertEquals("1.0", mavenCoordinates.version)
    assertEquals("jar", mavenCoordinates.packaging)
    assertNull(mavenCoordinates.classifier)

    val modelVersionBefore = workspaceVersion()
    val roots = RepositoryUtils.loadDependenciesToLibrary(projectRule.project, library as LibraryEx, false, false, null)
      .blockingGet(1, TimeUnit.MINUTES)!!

    assertTrue(jar.exists(), "$jar should exist")
    assertEquals(1, roots.size)
    assertEquals(OrderRootType.CLASSES, roots[0].type)
    assertEquals(VfsUtil.getUrlForLibraryRoot(jar), roots[0].file.url)

    assertEquals(listOf(OrderRootType.CLASSES to VfsUtil.getUrlForLibraryRoot(jar)), getLibraryRoots(library).toList())
    assertTrue(workspaceVersion() > modelVersionBefore)
  }

  @Test
  fun libraryNoUpdateProjectModel() {
    val jar = localMavenCache.rootPath.resolve(GROUP_NAME).resolve(ARTIFACT_NAME).resolve("1.0").resolve("$ARTIFACT_NAME-1.0.jar")
    assertFalse(jar.exists())
    val jarUrl = VfsUtil.getUrlForLibraryRoot(jar)

    val library = createLibrary {
      it.addRoot(jarUrl, OrderRootType.CLASSES)
    }

    val modelVersionBefore = workspaceVersion()
    RepositoryUtils.loadDependenciesToLibrary(projectRule.project, library as LibraryEx, false, false, null)
      .blockingGet(1, TimeUnit.MINUTES)!!
    assertTrue(jar.exists(), "$jar should exist")
    assertEquals(listOf(OrderRootType.CLASSES to jarUrl), getLibraryRoots(library).toList())
    assertTrue(workspaceVersion() == modelVersionBefore)
  }

  private fun workspaceVersion() = (WorkspaceModel.getInstance(projectRule.project) as WorkspaceModelInternal).entityStorage.version
}