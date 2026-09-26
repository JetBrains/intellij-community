// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.idea.TestFor
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory

class JarRepositoryManagerTest : UsefulTestCase() {

  private lateinit var myProject: Project
  private lateinit var myFixture: IdeaProjectTestFixture
  private lateinit var myMavenRepo: File
  private lateinit var myTestLocalMvnCache: File
  private lateinit var myTestRepo: RemoteRepositoryDescription
  private var myOldTestRepo: String? = null

  override fun setUp() {
    super.setUp()
    myFixture = runInEdtAndGet {
      IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(getTestName(false)).fixture.apply { setUp() }
    }
    myProject = myFixture.project
    myMavenRepo = FileUtil.createTempDirectory("maven", "repo")
    myTestLocalMvnCache = FileUtil.createTempDirectory("maven", "cache")
    myTestRepo = RemoteRepositoryDescription("id", "name", myMavenRepo.toURI().toURL().toString())
    myOldTestRepo =  PathMacros.getInstance().getValue("MAVEN_REPOSITORY")
    PathMacros.getInstance().setMacro("MAVEN_REPOSITORY", myTestLocalMvnCache.absolutePath)
  }

  override fun tearDown() {
    try {
      PathMacros.getInstance().setMacro("MAVEN_REPOSITORY", myOldTestRepo)
      runInEdtAndWait {
        myFixture.tearDown()
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }


  @Test
  fun `test resolving annotations artifacts`() {
    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "1.0")
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)

    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
  }

  @Test
  fun `test resolving annotations artifacts without transitive dependencies`() {
    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "1.0",
                                                          false, emptyList())
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)
    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
  }

  @Test
  fun `test resolving latest annotations artifact`() {
    val expectedName = MavenRepoFixture(myMavenRepo).run {
      addAnnotationsArtifact(version = "1.0")
      addAnnotationsArtifact(version = "1.0-an1")
      val name = addAnnotationsArtifact(version = "1.0-an2")
      generateMavenMetadata("myGroup", "myArtifact")
      name
    }


    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "1.0")
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)

    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
    assertTrue("File name [${root.file.name}] should contain '$expectedName'", root.file.name.contains(expectedName))
  }

  @Test
  fun `test fallback to previous major annotations version`() {
    val expectedName = MavenRepoFixture(myMavenRepo).run {
      addAnnotationsArtifact(version = "1.0")
      addAnnotationsArtifact(version = "2.0")
      addAnnotationsArtifact(version = "2.0-an1")
      addAnnotationsArtifact(version = "2.0-an2")
      val name = addAnnotationsArtifact(version = "2.1-an1")
      generateMavenMetadata("myGroup", "myArtifact")
      name
    }

    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "2.5")
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)

    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
    assertTrue("File name [${root.file.name} should contain '$expectedName'", root.file.name.contains(expectedName))
  }


  @Test
  fun `test selection for interval`() {
    val expectedName = MavenRepoFixture(myMavenRepo).run {
      addAnnotationsArtifact(version = "1.0")
      addAnnotationsArtifact(version = "2.0")
      addAnnotationsArtifact(version = "2.0-an1")
      val name = addAnnotationsArtifact(version = "2.0-an2")
      addAnnotationsArtifact(version = "2.1-an1")

      generateMavenMetadata("myGroup", "myArtifact")
      name
    }

    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "[2.0, 2.1)")
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)

    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
    assertTrue("File name [${root.file.name} should contain '$expectedName'", root.file.name.contains(expectedName))
  }

  @Test
  fun `test selection for snapshot`() {
    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1-SNAPSHOT-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "1-SNAPSHOT")
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)

    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
  }

  @Test
  fun `test remote repositories selection uses project repos by default`() {
    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(
      RemoteRepositoryDescription("repo1", "repo1", "https://example.com/repo1"),
      RemoteRepositoryDescription("repo2", "repo2", "https://example.com/repo2"),
    )

    val descriptor = createDescriptorWithJarRepoId(null)
    val expected = RemoteRepositoriesConfiguration.getInstance(myProject).repositories
    val actualWhenNullPassed = JarRepositoryManager.selectRemoteRepositories(myProject, descriptor, emptyList())
    assertEquals(expected, actualWhenNullPassed)

    val actualWhenEmptyListPassed = JarRepositoryManager.selectRemoteRepositories(myProject, descriptor, emptyList())
    assertEquals(expected, actualWhenEmptyListPassed)
  }

  @Test
  fun `test remote repositories selection repo from desc has second priority`() {
    val repo1 = RemoteRepositoryDescription("repo1", "repo1", "https://example.com/repo1")
    val repo2 = RemoteRepositoryDescription("repo2", "repo2", "https://example.com/repo2")

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(repo1, repo2)
    val descriptor = createDescriptorWithJarRepoId(repo1.id)

    val expected = listOf(repo1)
    val actualWhenNullPassed = JarRepositoryManager.selectRemoteRepositories(myProject, descriptor, null)
    val actualWhenEmptyListPassed = JarRepositoryManager.selectRemoteRepositories(myProject, descriptor, emptyList())
    assertEquals(expected, actualWhenNullPassed)
    assertEquals(expected, actualWhenEmptyListPassed)
  }

  @Test
  fun `test remote repositories selection explicitly set repos have max priority`() {
    val repo1 = RemoteRepositoryDescription("repo1", "repo1", "https://example.com/repo1")
    val repo2 = RemoteRepositoryDescription("repo2", "repo2", "https://example.com/repo2")

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(repo1, repo2)
    val descriptor = createDescriptorWithJarRepoId(repo1.id)
    val expected = listOf(repo2)
    val actual = JarRepositoryManager.selectRemoteRepositories(myProject, descriptor, expected)
    assertEquals(expected, actual)
  }

  private fun createDescriptorWithJarRepoId(jarRepoId: String?) = JpsMavenRepositoryLibraryDescriptor("id", false, emptyList(),
                                                                                                      emptyList(),
                                                                                                      jarRepoId)

  private fun getResultingRoots(promise: Promise<MutableList<OrderRoot>>): List<OrderRoot>? {
    var result: List<OrderRoot>? = null
    (1..5).forEach {
      try {
        UIUtil.dispatchAllInvocationEvents()
        result = promise.blockingGet(1, TimeUnit.SECONDS)
        return@forEach
      }
      catch (e: TimeoutException) {
      }
    }
    return result
  }

  @TestFor(issues = ["IDEA-370993"])
  //remove when IDEA-372163 is ready
  @Test
  fun testShouldResolveToProjectAwareMavenRepoIfSet() {
    val myLocalProjectRepo = createTempDirectory()
    myProject.replaceService(PathMacroManager::class.java, object : PathMacroManager(null) {
      override fun expandPath(text: String?): String? {
        return if (text == JarRepositoryManager.MAVEN_REPOSITORY_MACRO) myLocalProjectRepo.absolutePathString()
        else text
      }
    }, testRootDisposable)

    MavenRepoFixture(myMavenRepo).apply {
      addLibraryArtifact(group = "myGroup", artifact = "myArtifact", version = "1.0")
      generateMavenMetadata("group", "artifact")
    }

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(
      RemoteRepositoryDescription("id", "name", myMavenRepo.toURI().toURL().toString())
    )

    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "1.0")
    val promise = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ARTIFACT),
                                                             listOf(myTestRepo), null)

    val result = getResultingRoots(promise)
    assertEquals(1, result!!.size)
    val root = result?.get(0)!!
    assertEquals(OrderRootType.CLASSES, root.type)
    assertTrue("files should be downloaded into project defined repo",
               root.file.toString().startsWith("jar://$myLocalProjectRepo"))
  }
}


