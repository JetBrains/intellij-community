// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation.location

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.containers.MultiMap
import org.junit.Test

class AnnotationsLocationSearcherTest : LightPlatformTestCase() {

  private fun configureExtensionPoint(secondProvider: AnnotationsLocationProvider? = null) {
    val testAnnotationProvider = TestAnnotationProvider()
    testAnnotationProvider.register("known-library-name", AnnotationsLocation("group",
                                                                              "artifact-id",
                                                                              "1.0",
                                                                              "file:///my-repo"))
    ExtensionTestUtil.maskExtensions(AnnotationsLocationProvider.EP_NAME, listOfNotNull(testAnnotationProvider, secondProvider), testRootDisposable)
  }

  @Test
  fun testUnknownLibrary() {
    configureExtensionPoint()

    val library = createLibrary("unknown-library")
    assertEmpty(AnnotationsLocationSearcher.findAnnotationsLocation(project, library, null, null, null))
  }

  @Test
  fun testKnownLibrary() {
    configureExtensionPoint()

    val library = createLibrary("known-library-name")
    assertSize(1, AnnotationsLocationSearcher.findAnnotationsLocation(project, library, null, null, null))
  }

  @Test
  fun testAllProvidersCalled() {
    val secondProvider = TestAnnotationProvider()
    secondProvider.register("known-library-name", AnnotationsLocation(
      "new_group",
      "new_artifact",
      "myVersion",
      "file:///other-repo"
    ))

    configureExtensionPoint(secondProvider)
    val library = createLibrary("known-library-name")
    assertSize(2, AnnotationsLocationSearcher.findAnnotationsLocation(project, library, null, null, null))
  }

  private fun createLibrary(libraryName: String): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = runWriteActionAndWait { libraryTable.createLibrary(libraryName) }
    disposeOnTearDown(object : Disposable {
      override fun dispose() {
        runWriteActionAndWait { libraryTable.removeLibrary(library) }
      }
    })
    return library
  }
}

private class TestAnnotationProvider : AnnotationsLocationProvider {
  private val myLibraryLocationMap = MultiMap.createLinked<String, AnnotationsLocation>()

  override fun getLocations(project: Project,
                            library: Library,
                            artifactId: String?,
                            groupId: String?,
                            version: String?): MutableCollection<AnnotationsLocation> = myLibraryLocationMap[library.name]

  fun register(name: String, location: AnnotationsLocation) {
    myLibraryLocationMap.putValue(name, location)
  }
}