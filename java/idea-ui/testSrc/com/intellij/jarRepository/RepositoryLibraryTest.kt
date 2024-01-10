// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.java.library.getMavenCoordinates
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

class RepositoryLibraryTest {
  companion object {
    @JvmStatic
    @get:ClassRule
    val applicationRule = ApplicationRule()
  }

  private val disposableRule = DisposableRule()
  private val projectRule = ProjectModelRule()
  private val mavenRepo = TempDirectory()
  private val localMavenCache = TempDirectory()
  @get:Rule
  val rulesChain = RuleChain(localMavenCache, mavenRepo, projectRule, disposableRule)

  private val ARTIFACT_NAME = "myArtifact"
  private val GROUP_NAME = "myGroup"
  private val LIBRARY_NAME = "NewLibrary"

  @Before
  fun setUp() {
    JarRepositoryManager.setLocalRepositoryPath(localMavenCache.root)

    MavenRepoFixture(mavenRepo.root).apply {
      addLibraryArtifact(group = GROUP_NAME, artifact = ARTIFACT_NAME, version = "1.0")
      generateMavenMetadata(GROUP_NAME, ARTIFACT_NAME)
    }

    RemoteRepositoriesConfiguration.getInstance(projectRule.project).repositories = listOf(
      RemoteRepositoryDescription("id", "name", mavenRepo.root.toURI().toURL().toString())
    )
  }

  private fun createLibrary(block: (LibraryEx.ModifiableModelEx) -> Unit = {}): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(projectRule.project)
    val library = runWriteActionAndWait {
      projectRule.addProjectLevelLibrary(LIBRARY_NAME) {
        it.kind = RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
        it.properties = RepositoryLibraryProperties(GROUP_NAME, ARTIFACT_NAME, "1.0", false, emptyList())
        block(it)
      }
    }
    disposableRule.register {
      runWriteActionAndWait { libraryTable.removeLibrary(library) }
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

    assertTrue(jar.exists())
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
    assertTrue(jar.exists())

    assertEquals(listOf(OrderRootType.CLASSES to jarUrl), getLibraryRoots(library).toList())
    assertTrue(workspaceVersion() == modelVersionBefore)
  }

  private fun workspaceVersion() = (WorkspaceModel.getInstance(projectRule.project) as WorkspaceModelImpl).entityStorage.version
}