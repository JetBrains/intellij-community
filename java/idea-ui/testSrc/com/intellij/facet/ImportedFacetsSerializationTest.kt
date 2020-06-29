// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet

import com.intellij.facet.mock.MockFacetConfiguration
import com.intellij.openapi.roots.ProjectModelExternalSource
import org.jetbrains.jps.model.serialization.facet.FacetState
import org.junit.Assume

class ImportedFacetsSerializationTest : FacetTestCase() {
  override fun isCreateDirectoryBasedProject() = true

  override fun isCreateProjectFileExplicitly() = false

  fun `test regular facet`() {
    addFacet()
    assertEmpty(getExternalFacetStates())
    val state = assertOneElement(facetManager.state.facets)
    assertEquals("name", state.name)
    assertNull(state.externalSystemId)
    assertNull(FacetFromExternalSourcesStorage.getInstance(module).externalSource)
  }

  fun `test imported facet`() {
    addFacet(createFacet("name"), MOCK_EXTERNAL_SOURCE)
    assertEmpty(facetManager.state.facets)
    val state = assertOneElement(getExternalFacetStates())
    assertEquals("name", state.name)
    assertNotNull(state.configuration)
    assertEquals("mock", state.externalSystemId)
    assertEquals("mock", FacetFromExternalSourcesStorage.getInstance(module).externalSource?.id)
  }

  fun `test regular facet and sub-facet`() {
    createFacetAndSubFacet(null, null)
    assertEmpty(getExternalFacetStates())
    val state = assertOneElement(facetManager.state.facets)
    checkFacetAndSubFacetState(state, true, true)
    reloadStateAndCheckFacetAndSubFacet(null, null)
  }

  fun `test imported facet and sub-facet`() {
    createFacetAndSubFacet(MOCK_EXTERNAL_SOURCE, MOCK_EXTERNAL_SOURCE)
    assertEmpty(facetManager.state.facets)
    val state = assertOneElement(getExternalFacetStates())
    checkFacetAndSubFacetState(state, true, true)
    reloadStateAndCheckFacetAndSubFacet(MOCK_EXTERNAL_SOURCE, MOCK_EXTERNAL_SOURCE)
  }

  fun `test imported facet and regular sub-facet`() {
    createFacetAndSubFacet(MOCK_EXTERNAL_SOURCE, null)
    val state = assertOneElement(facetManager.state.facets)
    checkFacetAndSubFacetState(state, false, true)

    val externalState = assertOneElement(getExternalFacetStates())
    checkFacetAndSubFacetState(externalState, true, false)
    reloadStateAndCheckFacetAndSubFacet(MOCK_EXTERNAL_SOURCE, null)
  }

  fun `test regular facet and imported sub-facet`() {
    createFacetAndSubFacet(null, MOCK_EXTERNAL_SOURCE)
    val state = assertOneElement(facetManager.state.facets)
    checkFacetAndSubFacetState(state, true, false)

    val externalState = assertOneElement(getExternalFacetStates())
    checkFacetAndSubFacetState(externalState, false, true)
    reloadStateAndCheckFacetAndSubFacet(null, MOCK_EXTERNAL_SOURCE)
  }

  private fun createFacetAndSubFacet(facetSource: ProjectModelExternalSource?, subFacetSource: ProjectModelExternalSource?) {
    val facet = addFacet(createFacet("name"), facetSource)
    facet.configuration.data = "1"
    val subFacet = addSubFacet(facet, "sub", subFacetSource)
    subFacet.configuration.data = "2"
  }

  private fun reloadStateAndCheckFacetAndSubFacet(facetSource: ProjectModelExternalSource?, subFacetSource: ProjectModelExternalSource?) {
    facetManager.loadState(facetManager.state)
    val facets = facetManager.sortedFacets
    assertEquals(2, facets.size)
    val (facet, subFacet) = facets
    assertEquals("name", facet.name)
    assertEquals(facetSource?.id, facet.externalSource?.id)
    assertEquals("1", (facet.configuration as MockFacetConfiguration).data)

    assertEquals(facet, subFacet.underlyingFacet)
    assertEquals("sub", subFacet.name)
    assertEquals(subFacetSource?.id, subFacet.externalSource?.id)
    assertEquals("2", (subFacet.configuration as MockFacetConfiguration).data)
  }

  private fun checkFacetAndSubFacetState(state: FacetState, hasConfiguration: Boolean, hasConfigurationOfSubFacet: Boolean) {
    assertEquals("name", state.name)
    if (hasConfiguration) {
      assertNotNull(state.configuration)
    }
    else {
      assertNull(state.configuration)
    }

    if (hasConfigurationOfSubFacet) {
      val subState = assertOneElement(state.subFacets)
      assertEquals("sub", subState.name)
      assertNotNull(subState.configuration)
    }
    else {
      assertEmpty(state.subFacets)
    }
  }

  override fun getFacetManager(): FacetManagerImpl {
    val facetManager = super.getFacetManager()
    Assume.assumeTrue("Test is ignored in workspace model", facetManager is FacetManagerImpl)
    return facetManager as FacetManagerImpl
  }

  private fun getExternalFacetStates() = FacetFromExternalSourcesStorage.getInstance(module).state.facets
}

private val MOCK_EXTERNAL_SOURCE = object: ProjectModelExternalSource {
  override fun getDisplayName() = "mock"

  override fun getId() = "mock"
}