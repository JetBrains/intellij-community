// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ExternalAnnotationsRepositoryResolverTest: LibraryTest() {
  private lateinit var annotationsRootType: OrderRootType

  override fun setUp() {
    super.setUp()
    annotationsRootType = AnnotationOrderRootType.getInstance()
  }

  @Test fun testAnnotationsResolution() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    val promise = resolver.resolveAsync(myProject, library, "myGroup:myArtifact:1.0")
    val result = getResult(promise)
    assertNotNull(result)
    result!!
    assertTrue(result.getFiles(annotationsRootType).isNotEmpty())
  }

  @Test fun testAnnotationsSyncResolution() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    resolver.resolve(myProject, library, "myGroup:myArtifact:1.0")
    assertTrue(library.getFiles(annotationsRootType).isNotEmpty())
  }

  @Test fun testAnnotationsSyncResolutionUsingLocation() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    resolver.resolve(myProject, library, AnnotationsLocation("myGroup", "myArtifact", "1.0", myMavenRepoDescription.url))
    assertTrue(library.getFiles(annotationsRootType).isNotEmpty())
  }

  @Test fun testThirdPartyAnnotationsResolution() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

    MavenRepoFixture(myMavenRepo).apply {
      addLibraryArtifact(version = "1.0")
      addAnnotationsArtifact(artifact = "myArtifact-annotations", version = "1.0")
      generateMavenMetadata("myGroup", "myArtifact")
      generateMavenMetadata("myGroup", "myArtifact-annotations")
    }

    resolver.resolve(myProject, library, "myGroup:myArtifact:1.0")
    assertTrue(library.getFiles(annotationsRootType).isNotEmpty())
  }

  @Test fun testThirdPartyAnnotationsResolutionAsync() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

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
    assertTrue(result.getFiles(annotationsRootType).isNotEmpty())
  }


  @Test fun `test select annotations artifact when newer library artifacts are available`() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

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
    assertTrue("Annotations root is not attached to library", library.getFiles(annotationsRootType).isNotEmpty())
  }


  @Test fun `test annotations resolution overrides existing roots`() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()
    val modifiableModel = library.modifiableModel

    modifiableModel.addRoot("file:///fake.url", annotationsRootType)
    runWriteAction { modifiableModel.commit() }

    assertThat(library.getUrls(annotationsRootType).single())
      .endsWith("/fake.url")

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    resolver.resolve(myProject, library, "myGroup:myArtifact:1.0")
    assertThat(library.getUrls(annotationsRootType).single())
      .endsWith("myGroup/myArtifact/1.0-an1/myArtifact-1.0-an1-annotations.zip!/")
  }


  @Test fun `test RootSetChanged should not be triggered resolving same artifact`() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()
    var libraryRootsChangedCounter = 0
    library.rootProvider.addRootSetChangedListener {
      libraryRootsChangedCounter++
    }

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(artifact = "myArtifact", version = "1.0")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    resolver.resolve(myProject, library, "myGroup:myArtifact:1.0")

    val modifiableModel = library.modifiableModel
    modifiableModel.addRoot("file:///fake.source", OrderRootType.SOURCES)
    runWriteAction { modifiableModel.commit() }

    resolver.resolve(myProject, library, "myGroup:myArtifact:1.0")

    assertEquals(2, libraryRootsChangedCounter)
  }


  @Test fun `test annotations resolution using Workspace API`() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    val workspaceModel = myProject.workspaceModel
    val diff = MutableEntityStorage.from(workspaceModel.currentSnapshot)
    resolver.resolve(myProject, library, AnnotationsLocation("myGroup", "myArtifact", "1.0", myMavenRepoDescription.url), diff)
    runWriteAction { workspaceModel.updateProjectModel("applying changes after test") { it.applyChangesFrom(diff)} }
    assertTrue(library.getFiles(annotationsRootType).isNotEmpty())
  }

  @Test fun `test annotations resolution using Workspace API overrides existing roots`() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()
    val modifiableModel = library.modifiableModel

    modifiableModel.addRoot("file:///fake.url", annotationsRootType)
    runWriteAction { modifiableModel.commit() }

    assertThat(library.getUrls(annotationsRootType).single())
      .endsWith("/fake.url")

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(version = "1.0-an1")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    val workspaceModel = myProject.workspaceModel
    val diff = MutableEntityStorage.from(workspaceModel.currentSnapshot)
    resolver.resolve(myProject, library,  AnnotationsLocation("myGroup", "myArtifact", "1.0", myMavenRepoDescription.url), diff)
    runWriteAction { workspaceModel.updateProjectModel("applying changes after test") { it.applyChangesFrom(diff)} }

    assertThat(library.getUrls(annotationsRootType).single())
      .endsWith("myGroup/myArtifact/1.0-an1/myArtifact-1.0-an1-annotations.zip!/")
  }


  @OptIn(EntityStorageInstrumentationApi::class)
  @Test fun `test RootSetChanged should not be triggered resolving same artifact using Workspace API`() {
    val resolver = ExternalAnnotationsRepositoryResolver()
    val library = createLibrary()
    var libraryRootsChangedCounter = 0
    library.rootProvider.addRootSetChangedListener {
      libraryRootsChangedCounter++
    }

    RemoteRepositoriesConfiguration.getInstance(myProject).repositories = listOf(myMavenRepoDescription)

    MavenRepoFixture(myMavenRepo).apply {
      addAnnotationsArtifact(artifact = "myArtifact", version = "1.0")
      generateMavenMetadata("myGroup", "myArtifact")
    }

    val workspaceModel = myProject.workspaceModel
    val diff1= MutableEntityStorage.from(workspaceModel.currentSnapshot)
    resolver.resolve(myProject, library, AnnotationsLocation("myGroup", "myArtifact", "1.0", myMavenRepoDescription.url), diff1)
    runWriteAction { workspaceModel.updateProjectModel("applying changes after test") { it.applyChangesFrom(diff1)} } // first write operation

    val modifiableModel = library.modifiableModel
    modifiableModel.addRoot("file:///fake.source", OrderRootType.SOURCES)
    runWriteAction { modifiableModel.commit() } // second write operation

    val diff2 = MutableEntityStorage.from(workspaceModel.currentSnapshot) as MutableEntityStorageInstrumentation
    resolver.resolve(myProject, library, AnnotationsLocation("myGroup", "myArtifact", "1.0", myMavenRepoDescription.url), diff2)
    assertFalse(diff2.hasChanges())
    runWriteAction { workspaceModel.updateProjectModel("applying changes after test") { it.applyChangesFrom(diff2)} } // third write operation

    assertTrue("Last library update should not cause root change events", libraryRootsChangedCounter < 3)
  }


}