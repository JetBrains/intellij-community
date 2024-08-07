// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.EXTERNAL
import com.intellij.openapi.externalSystem.util.Parallel.Companion.parallel

// Test for the mock auto-sync test infrastructure
class MockProjectAwareTest : AutoReloadParallelTestCase() {

  fun `test wait for single mock reload function (parallel)`() {
    test {
      enableAsyncExecution()

      waitForAllProjectActivities {
        forceReloadProject()
      }
      assertStateAndReset(numReload = 1, notified = false, event = "project reload")
    }
  }

  fun `test wait for mock reload function (parallel)`() {
    test {
      enableAsyncExecution()

      val expectedReloads = 10
      parallel {
        repeat(expectedReloads) {
          thread {
            waitForAllProjectActivities {
              forceReloadProject()
            }
          }
        }
      }
      assertStateAndReset(numReload = expectedReloads, notified = false, event = "$expectedReloads parallel project reloads")
    }
  }

  fun `test wait for indirect mock reload function (parallel)`() {
    test { settingsFile ->
      enableAsyncExecution()

      waitForAllProjectActivities {
        settingsFile.modify(EXTERNAL)
      }
      assertStateAndReset(numReload = 1, notified = false, event = "project reload")
    }
  }
}