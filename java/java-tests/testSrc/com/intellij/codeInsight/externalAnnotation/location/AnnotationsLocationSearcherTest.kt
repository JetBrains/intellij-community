// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation.location

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.containers.MultiMap
import org.junit.After
import org.junit.Before
import org.junit.Test

class AnnotationsLocationSearcherTest : LightPlatformTestCase() {
  private lateinit var testAnnotationProvider: TestAnnotationProvider
  private lateinit var ep: ExtensionPoint<AnnotationsLocationProvider>
  private lateinit var originalExtensions: List<AnnotationsLocationProvider>
  private lateinit var searcher: AnnotationsLocationSearcher

  @Before
  override fun setUp() {
    super.setUp()

    testAnnotationProvider = TestAnnotationProvider().apply {
      register("known-library-name", AnnotationsLocation("group",
                                                         "artifact-id",
                                                         "1.0",
                                                         "file:///my-repo"))
    }

    ep = Extensions.getRootArea().getExtensionPoint<AnnotationsLocationProvider>(AnnotationsLocationProvider.EP_NAME)
    originalExtensions = ep.extensionList
    ep.reset()
    ep.registerExtension(testAnnotationProvider)

    searcher = AnnotationsLocationSearcher.getInstance(getProject())
  }

  @Test
  fun testUnknownLibrary() {
    val library = createLibrary("unknown-library")
    assertEmpty(searcher.findAnnotationsLocation(library, null, null, null))
  }

  @Test
  fun testKnownLibrary() {
    val library = createLibrary("known-library-name")
    assertSize(1, searcher.findAnnotationsLocation(library, null, null, null))
  }

  @Test
  fun testAllProvidersCalled() {
    val secondProvider = TestAnnotationProvider().apply {
      register("known-library-name", AnnotationsLocation(
        "new_group",
        "new_artifact",
        "myVersion",
        "file:///other-repo"
      ))
    }
    ep.registerExtension(secondProvider)

    val library = createLibrary("known-library-name")
    assertSize(2, searcher.findAnnotationsLocation(library, null, null, null))
  }


  private fun createLibrary(libraryName: String): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    return WriteAction.compute<Library, RuntimeException> { libraryTable.createLibrary(libraryName) }
  }


  @After
  override fun tearDown() {
    try {
      ep.reset()
      originalExtensions.forEach { ep.registerExtension(it) }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}

class TestAnnotationProvider : AnnotationsLocationProvider {
  private val myLibraryLocationMap = MultiMap.createLinked<String, AnnotationsLocation>()
  override fun getLocations(library: Library,
                            artifactId: String?,
                            groupId: String?,
                            version: String?): MutableCollection<AnnotationsLocation> = myLibraryLocationMap[library.name]

  fun register(name: String, location: AnnotationsLocation) {
    myLibraryLocationMap.putValue(name, location)
  }
}