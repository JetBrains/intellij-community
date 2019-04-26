// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation.location

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.LightPlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test


class JBBundledAnnotationsProviderTest : LightPlatformTestCase() {

  @Test
  fun `test missing annotation is not provided`() {
    val provider = JBBundledAnnotationsProvider()
    val lib = createLibrary()

    val locations = provider.getLocations(lib, "myGroup", "missing-artifact", "1.0")

    assertThat(locations).hasSize(0)
  }

  @Test
  fun `test available annotation provided`() {
    val provider = JBBundledAnnotationsProvider()
    val lib = createLibrary()

    val locations = provider.getLocations(lib, "junit", "junit", "4.12")

    assertThat(locations)
      .hasSize(1)
      .usingElementComparatorIgnoringFields("myRepositoryUrls")
      .containsOnly(AnnotationsLocation("org.jetbrains.externalAnnotations.junit", "junit", "4.12-an1"))
  }

  @Test
  fun `test compatible version choice`() {
    val provider = JBBundledAnnotationsProvider()
    val lib = createLibrary()

    assertThat(provider.getLocations(lib, "junit", "junit", "4.1"))
      .hasSize(1)
      .usingElementComparatorIgnoringFields("myRepositoryUrls")
      .containsOnly(AnnotationsLocation("org.jetbrains.externalAnnotations.junit", "junit", "4.12-an1"))

    assertThat(provider.getLocations(lib, "junit", "junit", "4.9"))
      .hasSize(1)
      .usingElementComparatorIgnoringFields("myRepositoryUrls")
      .containsOnly(AnnotationsLocation("org.jetbrains.externalAnnotations.junit", "junit", "4.12-an1"))
  }

  @Test
  fun `test incompatible version skipped`() {
    val provider = JBBundledAnnotationsProvider()
    val lib = createLibrary()

    assertThat(provider.getLocations(lib, "junit", "junit", "3.9")).hasSize(0)
    assertThat(provider.getLocations(lib, "junit", "junit", "5.0")).hasSize(0)
  }


  private fun createLibrary(): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    return WriteAction.compute<Library, RuntimeException> { libraryTable.createLibrary("test-library") }
  }
}