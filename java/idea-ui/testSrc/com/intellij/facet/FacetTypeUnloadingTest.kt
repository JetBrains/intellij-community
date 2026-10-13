// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet

import com.intellij.facet.impl.invalid.InvalidFacetManager
import com.intellij.facet.mock.MockFacet
import com.intellij.facet.mock.MockFacetConfiguration
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.MockSubFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.facet.mock.runWithRegisteredFacetTypes
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.testFramework.HeavyPlatformTestCase
import java.util.concurrent.atomic.AtomicInteger

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
    assertEquals("<configuration data=\"my data\" />", JDOMUtil.write(invalidFacet.configuration.facetState.configuration!!))

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
    //suppressed until https://youtrack.jetbrains.com/issue/IDEA-294031 being fixed
    @Suppress("AssertBetweenInconvertibleTypes")
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
    //suppressed until https://youtrack.jetbrains.com/issue/IDEA-294031 being fixed
    @Suppress("AssertBetweenInconvertibleTypes")
    assertSame(mockFacet, invalidFacet.underlyingFacet)

    registerFacetType(MockSubFacetType(), testRootDisposable)
    assertTrue(InvalidFacetManager.getInstance(myProject).invalidFacets.isEmpty())
    val mockSubFacet = facetManager.getFacetsByType(MockSubFacetType.ID).single()
    assertEquals("sub-facet", mockSubFacet.name)
    //suppressed until https://youtrack.jetbrains.com/issue/IDEA-294031 being fixed
    @Suppress("AssertBetweenInconvertibleTypes")
    assertSame(mockFacet, mockSubFacet.underlyingFacet)
  }

  fun `test unload and load facets in two modules`() {
    val secondModule = createModule("second")
    val facetEvents = countFacetEvents()
    runWithRegisteredFacetTypes(MockFacetType()) {
      runWriteAction {
        for (m in listOf(module, secondModule)) {
          val model = FacetManager.getInstance(m).createModifiableModel()
          model.addFacet(MockFacet(m, "mock", MockFacetConfiguration("${m.name} data")))
          model.commit()
        }
      }
      facetEvents.set(0)
    }

    assertEquals(1, facetEvents.get())
    for (m in listOf(module, secondModule)) {
      assertTrue(FacetManager.getInstance(m).getFacetsByType(MockFacetType.ID).isEmpty())
    }
    assertSameElements(InvalidFacetManager.getInstance(myProject).invalidFacets.map { it.module }, module, secondModule)

    facetEvents.set(0)
    registerFacetType(MockFacetType(), testRootDisposable)
    assertEquals(1, facetEvents.get())
    assertTrue(InvalidFacetManager.getInstance(myProject).invalidFacets.isEmpty())
    for (m in listOf(module, secondModule)) {
      val mockFacet = FacetManager.getInstance(m).getFacetsByType(MockFacetType.ID).single()
      assertEquals("${m.name} data", mockFacet.configuration.data)
    }
  }

  private fun countFacetEvents(): AtomicInteger {
    val counter = AtomicInteger()
    myProject.messageBus.connect(testRootDisposable).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        if (event.getChanges(FacetEntity::class.java).isNotEmpty()) counter.incrementAndGet()
      }
    })
    return counter
  }

  override fun setUp() {
    super.setUp()
    //initialize facet types and register listeners
    FacetTypeRegistry.getInstance().facetTypes
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(testRootDisposable, {})
  }
}
