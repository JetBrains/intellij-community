// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.messages.MessageBus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
class AutoImportProjectTrackerLeakTest {

  private val project by projectFixture()

  @Test
  fun `test failed construction does not leak the tracker`() {
    val disposedProject = DisposedMessageBusProject(project)

    assertThrows<AlreadyDisposedException> {
      AutoImportProjectTracker(disposedProject)
    }

    LeakHunter.checkLeak(LeakHunter.allRoots(), DisposedMessageBusProject::class.java, null)
  }

  /**
   * Fails on [getMessageBus] like a project during its disposal.
   */
  private class DisposedMessageBusProject(project: Project) : Project by project {
    override fun getMessageBus(): MessageBus = throw AlreadyDisposedException("Already disposed")
  }
}
