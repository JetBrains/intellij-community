// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler

import com.intellij.java.workspace.entities.javaCompilerOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.testFramework.HeavyPlatformTestCase

/**
 * Tests for storing per-module Java compiler options in the workspace model
 * ([com.intellij.java.workspace.entities.JavaModuleCompilerOptionsEntity]) and reading them through
 * [JavaCompilerOptionsWorkspaceModel] (used by [WorkspaceModelJavaCompilerConfigurationProxy] for Java highlighting).
 */
class JavaCompilerOptionsWorkspaceModelTest : HeavyPlatformTestCase() {
  private val options = listOf("--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED")

  fun testRoundTripStoresAndReadsModuleOptions() {
    val module = createModule("foo")
    assertNull(JavaCompilerOptionsWorkspaceModel.getModuleAdditionalOptions(module))

    ApplicationManager.getApplication().runWriteAction {
      JavaCompilerOptionsWorkspaceModel.setModuleAdditionalOptions(module, options)
    }

    assertEquals(options, JavaCompilerOptionsWorkspaceModel.getModuleAdditionalOptions(module))
  }

  fun testUpdatingExistingModuleOptions() {
    val module = createModule("foo")
    ApplicationManager.getApplication().runWriteAction {
      JavaCompilerOptionsWorkspaceModel.setModuleAdditionalOptions(module, options)
    }
    val updated = listOf("--add-modules", "ALL-SYSTEM")
    ApplicationManager.getApplication().runWriteAction {
      JavaCompilerOptionsWorkspaceModel.setModuleAdditionalOptions(module, updated)
    }

    assertEquals(updated, JavaCompilerOptionsWorkspaceModel.getModuleAdditionalOptions(module))
  }

  fun testMissingEntityReturnsNullSoCallersFallBackToLegacy() {
    val withOptions = createModule("foo")
    val withoutOptions = createModule("bar")
    ApplicationManager.getApplication().runWriteAction {
      JavaCompilerOptionsWorkspaceModel.setModuleAdditionalOptions(withOptions, options)
    }

    // The module with its own entity is read from the workspace model...
    assertEquals(options, JavaCompilerOptionsWorkspaceModel.getModuleAdditionalOptions(withOptions))
    // ...while a module without one yields null, so the proxy falls back to the legacy compiler.xml storage.
    assertNull(JavaCompilerOptionsWorkspaceModel.getModuleAdditionalOptions(withoutOptions))
  }

  fun testEntityReusesModuleEntitySource() {
    val module = createModule("foo")
    ApplicationManager.getApplication().runWriteAction {
      JavaCompilerOptionsWorkspaceModel.setModuleAdditionalOptions(module, options)
    }

    val snapshot = project.workspaceModel.currentSnapshot
    val moduleEntity = snapshot.resolve(ModuleId(module.name))!!
    val optionsEntity = moduleEntity.javaCompilerOptions!!
    assertEquals(moduleEntity.entitySource, optionsEntity.entitySource)
    assertEquals(options, optionsEntity.additionalOptions)
  }
}
