// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.externalAnnotation.location

import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.codeInsight.externalAnnotation.location.JBBundledAnnotationsProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class JBBundledAnnotationsProviderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val project get() = projectFixture.get().project

  @TestDisposable
  lateinit var testRootDisposable: Disposable

  @Test
  fun `test missing annotation is not provided`() {
    val provider = JBBundledAnnotationsProvider()
    val lib = createLibrary()

    val locations = provider.getLocations(project, lib, "myGroup", "missing-artifact", "1.0")

    assertThat(locations).hasSize(0)
  }

  @Test
  fun `test available annotation provided`() {
    val provider = JBBundledAnnotationsProvider()
    val lib = createLibrary()

    val locations = provider.getLocations(project, lib, "junit", "junit", "4.12")

    val locationsAssert = assertThat(locations)
    locationsAssert
      .hasSize(1)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("myRepositoryUrls")
    locationsAssert.containsOnly(AnnotationsLocation("org.jetbrains.externalAnnotations.junit", "junit", "4.12-an1"))
  }

  @Test
  fun `test compatible version choice`() {
    val provider = JBBundledAnnotationsProvider()
    val lib = createLibrary()

    var locationsAssert = assertThat(provider.getLocations(project, lib, "junit", "junit", "4.1"))
    locationsAssert
      .hasSize(1)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("myRepositoryUrls")
    locationsAssert.containsOnly(AnnotationsLocation("org.jetbrains.externalAnnotations.junit", "junit", "4.12-an1"))

    locationsAssert = assertThat(provider.getLocations(project, lib, "junit", "junit", "4.9"))
    locationsAssert
      .hasSize(1)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields("myRepositoryUrls")
    locationsAssert.containsOnly(AnnotationsLocation("org.jetbrains.externalAnnotations.junit", "junit", "4.12-an1"))
  }

  @Test
  fun `test incompatible version skipped`() {
    val provider = JBBundledAnnotationsProvider()
    val lib = createLibrary()

    assertThat(provider.getLocations(project, lib, "junit", "junit", "3.9")).hasSize(0)
    assertThat(provider.getLocations(project, lib, "junit", "junit", "5.0")).hasSize(0)
  }


  private fun createLibrary(): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = runWriteActionAndWait { libraryTable.createLibrary("test-library") }
    Disposer.register(testRootDisposable, object : Disposable {
      override fun dispose() {
        runWriteActionAndWait { libraryTable.removeLibrary(library) }
      }
    })
    return library
  }
}