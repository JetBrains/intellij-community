// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots

import com.intellij.java.configurationStore.saveProjectState
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * Checks that proper [com.intellij.openapi.roots.ModuleRootListener.rootsChanged] are sent for changes in module roots.
 */
class ModuleRootsChangedTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var moduleRootListener: RootsChangedTest.MyModuleRootListener
  private lateinit var module: Module

  @Before
  fun setUp() {
    module = projectModel.createModule("main")
    moduleRootListener = RootsChangedTest.MyModuleRootListener(projectModel.project)
    projectModel.project.messageBus.connect(projectModel.project).subscribe(ModuleRootListener.TOPIC, moduleRootListener)
  }

  @Test
  fun `no rootsChanged on saving iml file`() {
    projectModel.saveProjectState()
    moduleRootListener.assertNoEvents(true)
  }
}