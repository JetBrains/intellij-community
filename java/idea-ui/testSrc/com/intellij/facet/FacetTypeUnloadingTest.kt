// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet

import com.intellij.facet.impl.invalid.InvalidFacetManager
import com.intellij.facet.mock.*
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.HeavyPlatformTestCase

class FacetTypeUnloadingTest : HeavyPlatformTestCase() {
  fun `test unload and load facet`() {
    val facetManager = FacetManager.getInstance(module)
    val addedFacet = runWithRegisteredFacetTypes(MockFacetType()) {
      runWriteAction {
        val model = facetManager.createModifiableModel()
        val facet = MockFacet(module, "mock", MockFacetConfiguration("my data"))
        model.addFacet(facet)
        model.commit()
        assertTrue(facet.isInitialized)
        assertFalse(facet.isDisposed)
        assertEquals("mock", facetManager.getFacetsByType(MockFacetType.ID).single().name)
        return@runWriteAction facet
      }
    }

    assertTrue(facetManager.getFacetsByType(MockFacetType.ID).isEmpty())
    assertTrue(addedFacet.isDisposed)
    val invalidFacet = InvalidFacetManager.getInstance(myProject).invalidFacets.single()
    assertEquals("mock", invalidFacet.name)
    assertEquals("<configuration data=\"my data\" />", JDOMUtil.write(invalidFacet.configuration.facetState.configuration))

    registerFacetType(MockFacetType(), testRootDisposable)
    assertTrue(InvalidFacetManager.getInstance(myProject).invalidFacets.isEmpty())
    val mockFacet = facetManager.getFacetsByType(MockFacetType.ID).single()
    assertEquals("mock", mockFacet.name)
    assertEquals("my data", mockFacet.configuration.data)
    assertTrue(mockFacet.isInitialized)
    assertFalse(mockFacet.isDisposed)
  }

  fun `test unload and load facet with sub facets`() {
    val facetManager = FacetManager.getInstance(module)
    runWithRegisteredFacetTypes(MockFacetType(), MockSubFacetType()) {
      runWriteAction {
        val model = facetManager.createModifiableModel()
        val facet = MockFacet(module, "mock")
        model.addFacet(facet)
        model.addFacet(MockSubFacetType.getInstance().createFacet(module, "sub-facet", MockFacetConfiguration(), facet))
        model.commit()
      }
      assertEquals("mock", facetManager.getFacetsByType(MockFacetType.ID).single().name)
      assertEquals("sub-facet", facetManager.getFacetsByType(MockSubFacetType.ID).single().name)
    }

    assertTrue(facetManager.getFacetsByType(MockFacetType.ID).isEmpty())
    assertTrue(facetManager.getFacetsByType(MockSubFacetType.ID).isEmpty())
    val invalidFacet = InvalidFacetManager.getInstance(myProject).invalidFacets.single()
    assertEquals("mock", invalidFacet.name)
    val subFacetState = invalidFacet.configuration.facetState.subFacets.single()
    assertEquals("sub-facet", subFacetState.name)

    registerFacetType(MockFacetType(), testRootDisposable)
    registerFacetType(MockSubFacetType(), testRootDisposable)

    assertTrue(InvalidFacetManager.getInstance(myProject).invalidFacets.isEmpty())
    val mockFacet = facetManager.getFacetsByType(MockFacetType.ID).single()
    assertEquals("mock", mockFacet.name)
    val mockSubFacet = facetManager.getFacetsByType(MockSubFacetType.ID).single()
    assertEquals("sub-facet", mockSubFacet.name)
    assertSame(mockFacet, mockSubFacet.underlyingFacet)
  }

  fun `test unload and load sub facet`() {
    val facetManager = FacetManager.getInstance(module)
    registerFacetType(MockFacetType(), testRootDisposable)
    runWithRegisteredFacetTypes(MockSubFacetType()) {
      runWriteAction {
        val model = facetManager.createModifiableModel()
        val facet = MockFacet(module, "mock")
        model.addFacet(facet)
        model.addFacet(MockSubFacetType.getInstance().createFacet(module, "sub-facet", MockFacetConfiguration(), facet))
        model.commit()
      }
      assertEquals("mock", facetManager.getFacetsByType(MockFacetType.ID).single().name)
      assertEquals("sub-facet", facetManager.getFacetsByType(MockSubFacetType.ID).single().name)
    }

    val mockFacet = facetManager.getFacetsByType(MockFacetType.ID).single()
    assertEquals("mock", mockFacet.name)
    assertTrue(facetManager.getFacetsByType(MockSubFacetType.ID).isEmpty())
    val invalidFacet = InvalidFacetManager.getInstance(myProject).invalidFacets.single()
    assertEquals("sub-facet", invalidFacet.name)
    assertSame(mockFacet, invalidFacet.underlyingFacet)

    registerFacetType(MockSubFacetType(), testRootDisposable)
    assertTrue(InvalidFacetManager.getInstance(myProject).invalidFacets.isEmpty())
    val mockSubFacet = facetManager.getFacetsByType(MockSubFacetType.ID).single()
    assertEquals("sub-facet", mockSubFacet.name)
    assertSame(mockFacet, mockSubFacet.underlyingFacet)
  }

  override fun setUp() {
    super.setUp()
    //initialize facet types and register listeners
    FacetTypeRegistry.getInstance().facetTypes
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler({}, testRootDisposable)
  }
}