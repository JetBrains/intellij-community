// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet

import com.intellij.facet.impl.invalid.InvalidFacetManager
import com.intellij.facet.mock.MockFacet
import com.intellij.facet.mock.MockFacetConfiguration
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.MockSubFacetType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.HeavyPlatformTestCase

class FacetTypeUnloadingTest : HeavyPlatformTestCase() {
  fun `test unload and load facet`() {
    val facetManager = FacetManager.getInstance(module)
    runWithRegisteredFacetTypes(MockFacetType()) {
      runWriteAction {
        val configuration = MockFacetConfiguration().apply {
          data = "my data"
        }
        val model = facetManager.createModifiableModel()
        model.addFacet(MockFacet(module, "mock", configuration))
        model.commit()
      }
      assertEquals("mock", facetManager.getFacetsByType(MockFacetType.ID).single().name)
    }

    assertTrue(facetManager.getFacetsByType(MockFacetType.ID).isEmpty())
    val invalidFacet = InvalidFacetManager.getInstance(myProject).invalidFacets.single()
    assertEquals("mock", invalidFacet.name)
    assertEquals("<configuration data=\"my data\" />", JDOMUtil.write(invalidFacet.configuration.facetState.configuration))

    registerFacetType(MockFacetType(), testRootDisposable)
    assertTrue(InvalidFacetManager.getInstance(myProject).invalidFacets.isEmpty())
    val mockFacet = facetManager.getFacetsByType(MockFacetType.ID).single()
    assertEquals("mock", mockFacet.name)
    assertEquals("my data", mockFacet.configuration.data)
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

  private inline fun runWithRegisteredFacetTypes(vararg types: FacetType<*, *>, action: () -> Unit) {
    val disposable = Disposer.newDisposable()
    for (type in types) {
      registerFacetType(type, disposable)
    }

    try {
      action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun registerFacetType(type: FacetType<*, *>, disposable: Disposable) {
    val facetTypeDisposable = Disposer.newDisposable()
    Disposer.register(disposable, Disposable {
      runWriteAction {
        Disposer.dispose(facetTypeDisposable)
      }
    })
    FacetType.EP_NAME.getPoint(null).registerExtension(type, facetTypeDisposable)
  }

  override fun setUp() {
    super.setUp()
    //initialize facet types and register listeners
    FacetTypeRegistry.getInstance().facetTypes
  }
}