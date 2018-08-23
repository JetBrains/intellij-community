// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class JarRepositoryManagerTest : UsefulTestCase() {

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


  @Test
  fun `test resolving annotations artifacts`() {
    addAnnotationsArtifact(version = "1.0")
    generateMavenMetadata("myGroup", "myArtifact")

    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "1.0")
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)

    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
  }

  @Test fun `test resolving latest annotations artifact`() {
    addAnnotationsArtifact(version = "1.0")
    addAnnotationsArtifact(version = "1.0-an1")
    val expectedName = addAnnotationsArtifact(version = "1.0-an2")

    generateMavenMetadata("myGroup", "myArtifact")


    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "1.0")
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)

    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
    assertTrue("File name [${root.file.name}] should contain '$expectedName'", root.file.name.contains(expectedName))
  }

  @Test fun `test fallback to previous major annotations version`() {
    addAnnotationsArtifact(version = "1.0")
    addAnnotationsArtifact(version = "2.0")
    addAnnotationsArtifact(version = "2.0-an1")
    addAnnotationsArtifact(version = "2.0-an2")
    val expectedName = addAnnotationsArtifact(version = "2.1-an1")

    generateMavenMetadata("myGroup", "myArtifact")

    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "2.5")
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)

    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
    assertTrue("File name [${root.file.name} should contain '$expectedName'", root.file.name.contains(expectedName))
  }


  @Test fun `test selection for interval`() {
    addAnnotationsArtifact(version = "1.0")
    addAnnotationsArtifact(version = "2.0")
    addAnnotationsArtifact(version = "2.0-an1")
    val expectedName = addAnnotationsArtifact(version = "2.0-an2")
    addAnnotationsArtifact(version = "2.1-an1")

    generateMavenMetadata("myGroup", "myArtifact")

    val description = JpsMavenRepositoryLibraryDescriptor("myGroup", "myArtifact", "[2.0, 2.1)")
    val promise: Promise<MutableList<OrderRoot>> = JarRepositoryManager.loadDependenciesAsync(myProject, description, setOf(ArtifactKind.ANNOTATIONS),
                                                                                              listOf(myTestRepo), null)
    val result: List<OrderRoot>? = getResultingRoots(promise)

    assertEquals(1, result?.size)
    val root = result?.get(0)!!
    assertEquals(AnnotationOrderRootType.getInstance(), root.type)
    assertTrue("File name [${root.file.name} should contain '$expectedName'", root.file.name.contains(expectedName))
  }



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

  private fun addAnnotationsArtifact(group: String = "myGroup",
                                     artifact: String = "myArtifact",
                                     version: String)
    : String = File(myMavenRepo, "$group/$artifact/$version/$artifact-$version-annotations.zip")
    .apply {
      parentFile.mkdirs()
      writeText("Fake annotations artifact")
    }.name

  private fun generateMavenMetadata(group: String, artifact: String) {
    val metadata = File(myMavenRepo, "$group/$artifact/maven-metadata.xml")
      .apply {
        parentFile.mkdirs()
      }

    val versionsList = metadata.parentFile
      .listFiles()
      .asSequence()
      .filter { it.isDirectory }
      .map { it.name }
      .toList()

    val releaseVersion = versionsList.last()

    metadata.writeText("""
      |<?xml version="1.0" encoding="UTF-8"?>
      |<metadata>
      |  <groupId>$group</groupId>
      |  <artifactId>$artifact</artifactId>
      |  <version>$releaseVersion</version>
      |  <versioning>
      |    <latest>$releaseVersion</latest>
      |    <release>$releaseVersion</release>
      |    <versions>
      |      ${versionsList.joinToString(separator = "\n") { "<version>$it</version>" } }
      |    </versions>
      |    <lastUpdated>20180809190315</lastUpdated>
      |  </versioning>
      |</metadata>
""".trimMargin())

  }
}
