// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.facet.FacetManager
import com.intellij.facet.impl.FacetUtil
import com.intellij.facet.mock.MockFacetConfiguration
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.facet.mock.runWithRegisteredFacetTypes
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class SaveFacetsTest {
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
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler({}, disposable.disposable)
  }

  @Test
  fun `single facet`() {
    registerFacetType(MockFacetType(), disposable.disposable)
    val module = projectModel.createModule("foo")
    projectModel.addFacet(module, MockFacetType.getInstance(), MockFacetConfiguration("my-data"))
    projectModel.saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(configurationStoreTestDataRoot.resolve("single-facet")))
  }

  @Test
  fun `single invalid facet`() {
    val module = projectModel.createModule("foo")
    runWithRegisteredFacetTypes(MockFacetType()) {
      projectModel.addFacet(module, MockFacetType.getInstance(), MockFacetConfiguration("my-data"))
    }
    projectModel.saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(configurationStoreTestDataRoot.resolve("single-facet")))
  }
}