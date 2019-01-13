// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.jetbrains.concurrency.Promise
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ExternalAnnotationsRepositoryResolverTest: UsefulTestCase() {

  private lateinit var myProject: Project
  private lateinit var myFixture: IdeaProjectTestFixture
  private lateinit var myMavenRepo: File
  private lateinit var myTestLocalMvnCache: File
  private lateinit var myTestRepo: RemoteRepositoryDescription

  @Before
  override fun setUp() {
    super.setUp()
    myFixture = EdtTestUtil.runInEdtAndGet(ThrowableComputable {
      IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().fixture.apply { setUp() }
    })
    myProject = myFixture.project
    myMavenRepo = FileUtil.createTempDirectory("maven", "repo")
    myTestLocalMvnCache = FileUtil.createTempDirectory("maven", "cache")
    myTestRepo = RemoteRepositoryDescription("id", "name", myMavenRepo.toURI().toURL().toString())
    JarRepositoryManager.setLocalRepositoryPath(myTestLocalMvnCache)
  }

  @After
  override fun tearDown() {
    try {
      EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
        myFixture.tearDown()
      })
    } finally {
      super.tearDown()
    }
  }

  @Test fun testAnnotationsResolution() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = WriteAction.compute<Library, RuntimeException> { libraryTable.createLibrary("NewLibrary") }

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myTestRepo)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    val promise = resolver.resolveAsync(myProject, library, "myGroup:myArtifact:1.0")
    val result = getResult(promise)
    assertNotNull(result)
    result!!
    assertTrue(result.getFiles(AnnotationOrderRootType.getInstance()).isNotEmpty())
  }


  @Test fun testAnnotationsSyncResolution() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = WriteAction.compute<Library, RuntimeException> { libraryTable.createLibrary("NewLibrary") }

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myTestRepo)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    resolver.resolve(myProject, library, "myGroup:myArtifact:1.0")
    assertTrue(library.getFiles(AnnotationOrderRootType.getInstance()).isNotEmpty())
  }

  @Test fun testAnnotationsSyncResolutionUsingLocation() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = WriteAction.compute<Library, RuntimeException> { libraryTable.createLibrary("NewLibrary") }

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    resolver.resolve(myProject, library, AnnotationsLocation("myGroup", "myArtifact", "1.0", myTestRepo.url))
    assertTrue(library.getFiles(AnnotationOrderRootType.getInstance()).isNotEmpty())
  }

  @Test fun testThirdPartyAnnotationsResolution() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = WriteAction.compute<Library, RuntimeException> { libraryTable.createLibrary("NewLibrary") }

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myTestRepo)

    MavenRepoFixture(myMavenRepo).apply {
      addLibraryArtifact(version = "1.0")
      addAnnotationsArtifact(artifact = "myArtifact-annotations", version = "1.0")
      generateMavenMetadata("myGroup", "myArtifact")
      generateMavenMetadata("myGroup", "myArtifact-annotations")
    }

    resolver.resolve(myProject, library, "myGroup:myArtifact:1.0")
    assertTrue(library.getFiles(AnnotationOrderRootType.getInstance()).isNotEmpty())
  }

  @Test fun testThirdPartyAnnotationsResolutionAsync() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = WriteAction.compute<Library, RuntimeException> { libraryTable.createLibrary("NewLibrary") }

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myTestRepo)

    MavenRepoFixture(myMavenRepo).apply {
      addLibraryArtifact(version = "1.0")
      addAnnotationsArtifact(artifact = "myArtifact-annotations", version = "1.0")
      generateMavenMetadata("myGroup", "myArtifact")
      generateMavenMetadata("myGroup", "myArtifact-annotations")
    }

    val promise = resolver.resolveAsync(myProject, library, "myGroup:myArtifact:1.0")
    val result = getResult(promise)
    assertNotNull(result)
    result!!
    assertTrue(result.getFiles(AnnotationOrderRootType.getInstance()).isNotEmpty())
  }


  @Test fun `test select annotations artifact when newer library artifacts are available`() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = WriteAction.compute<Library, RuntimeException> { libraryTable.createLibrary("NewLibrary") }

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myTestRepo)

    MavenRepoFixture(myMavenRepo).apply {
      addLibraryArtifact(version = "1.0")
      addAnnotationsArtifact(version = "1.0")
      addAnnotationsArtifact(version = "1.0-an1")
      addAnnotationsArtifact(artifact = "myArtifact-annotations", version = "1.0")
      addAnnotationsArtifact(artifact = "myArtifact-annotations", version = "1.0-an1")
      addLibraryArtifact(version = "1.1")
      generateMavenMetadata("myGroup", "myArtifact")
      generateMavenMetadata("myGroup", "myArtifact-annotations")
    }

    resolver.resolve(myProject, library, "myGroup:myArtifact:1.1")
    assertTrue("Annotations root is not attached to library", library.getFiles(AnnotationOrderRootType.getInstance()).isNotEmpty())
  }


  @Test fun `test annotations resolution overrides existing roots`() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = WriteAction.compute<Library, RuntimeException> { libraryTable.createLibrary("NewLibrary") }
    val modifiableModel = library.modifiableModel
    modifiableModel.addRoot("file://fake.url", AnnotationOrderRootType.getInstance())
    runWriteAction { modifiableModel.commit() }

    assertTrue(library.getUrls(AnnotationOrderRootType.getInstance()).single() == "file://fake.url")

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myTestRepo)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    resolver.resolve(myProject, library, "myGroup:myArtifact:1.0")
    assertTrue(library.getUrls(AnnotationOrderRootType.getInstance()).single().endsWith("myGroup/myArtifact/1.0-an1/myArtifact-1.0-an1-annotations.zip!/"))
  }

  private fun <T> getResult(promise: Promise<T>): T? {
    var result: T? = null
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
}