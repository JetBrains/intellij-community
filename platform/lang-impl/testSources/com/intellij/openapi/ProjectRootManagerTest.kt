// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
internal class ProjectRootManagerTest {
  private val projectFixture = projectFixture()

  @Test
  fun testDisposedModule(): Unit = timeoutRunBlocking {
    val module = writeAction { ModuleManager.getInstance(projectFixture.get()).newNonPersistentModule("someModule", "someId") }
    writeAction {
      ModuleManager.getInstance(projectFixture.get()).disposeModule(module)
    }
    readAction {
      Assertions.assertThrows(AlreadyDisposedException::class.java, {
        ModuleRootManager.getInstance(module)
      }, "Manager should throw exception if module is disposed")
    }
  }
}
