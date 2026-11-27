// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots

import com.intellij.java.configurationStore.saveProjectState
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.testFramework.ModuleRootEventTracker
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Checks that proper [com.intellij.openapi.roots.ModuleRootListener.rootsChanged] are sent for changes in module roots.
 */
@TestApplication
class ModuleRootsChangedTest {
  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  private lateinit var moduleRootListener: ModuleRootEventTracker
  private lateinit var module: Module

  @BeforeEach
  fun setUp() {
    module = projectModel.createModule("main")
    moduleRootListener = ModuleRootEventTracker(projectModel.project)
    projectModel.project.messageBus.connect(projectModel.project).subscribe(ModuleRootListener.TOPIC, moduleRootListener)
  }

  @Test
  fun `no rootsChanged on saving iml file`() {
    projectModel.saveProjectState()
    moduleRootListener.assertNoEvents(true)
  }
}