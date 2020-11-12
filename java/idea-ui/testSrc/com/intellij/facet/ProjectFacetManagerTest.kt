// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet

import com.intellij.facet.FacetTestCase
import com.intellij.facet.ProjectFacetManager
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.MockFacet
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.facet.FacetManager
import com.intellij.facet.mock.MockFacetConfiguration
import com.intellij.facet.mock.registerFacetType
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProjectFacetManagerTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  val disposable = DisposableRule()

  @Before
  fun setUp() {
    registerFacetType(MockFacetType(), disposable.disposable)
  }

  @Test
  fun testHasFacets() {
    val manager = ProjectFacetManager.getInstance(projectModel.project)
    assertThat(manager.hasFacets(MockFacetType.ID)).isFalse
    val facet = projectModel.addFacet(projectModel.createModule(), MockFacetType.getInstance(), MockFacetConfiguration())
    assertThat(manager.hasFacets(MockFacetType.ID)).isTrue
    projectModel.removeFacet(facet)
    assertThat(manager.hasFacets(MockFacetType.ID)).isFalse
  }

  @Test
  fun testRemoveModuleWithFacet() {
    val module = projectModel.createModule()
    projectModel.addFacet(projectModel.createModule(), MockFacetType.getInstance(), MockFacetConfiguration())
    val manager = ProjectFacetManager.getInstance(projectModel.project)
    assertThat(manager.hasFacets(MockFacetType.ID)).isTrue
    projectModel.removeModule(module)
    assertThat(manager.hasFacets(MockFacetType.ID)).isFalse
  }
}