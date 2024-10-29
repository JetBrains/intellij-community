// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.EXTERNAL
import com.intellij.openapi.externalSystem.util.Parallel.Companion.parallel
import java.util.concurrent.CountDownLatch

class AutoReloadParallelTest : AutoReloadParallelTestCase() {

  fun `test merge started project reloads from explicit reload`() {
    test {
      enableAsyncExecution()
      setDispatcherMergingSpan(MERING_SPAN)

      repeat(TEST_ATTEMPTS) {
        parallel {
          thread {
            markDirty()
            waitForAllProjectActivities {
              scheduleProjectReload()
            }
          }
          thread {
            markDirty()
            waitForAllProjectActivities {
              scheduleProjectReload()
            }
          }
        }
        assertStateAndReset(numReload = 1, notified = false, event = "merged project reload")
      }
    }
  }

  fun `test merge project reloads from external modification`() {
    test { settingsFile ->
      enableAsyncExecution()
      setDispatcherMergingSpan(MERING_SPAN)

      repeat(TEST_ATTEMPTS) {
        parallel {
          thread {
            waitForAllProjectActivities {
              settingsFile.modify(EXTERNAL)
            }
          }
          thread {
            waitForAllProjectActivities {
              settingsFile.modify(EXTERNAL)
            }
          }
        }
        assertStateAndReset(numReload = 1, notified = false, event = "merged project reload")
      }
    }
  }

  fun `test merge project reloads from batch modification`() {
    test { settingsFile ->
      enableAsyncExecution()
      setDispatcherMergingSpan(MERING_SPAN)

      repeat(TEST_ATTEMPTS) {
        waitForAllProjectActivities {
          modification {
            settingsFile.modify(EXTERNAL)
          }
        }
        assertStateAndReset(numReload = 1, notified = false, event = "merged project reload")
      }
    }
  }

  fun `test merge project reloads with different nature`() {
    test { settingsFile ->
      enableAsyncExecution()
      setDispatcherMergingSpan(MERING_SPAN)

      repeat(TEST_ATTEMPTS) {
        parallel {
          thread {
            waitForAllProjectActivities {
              settingsFile.modify(EXTERNAL)
            }
          }
          thread {

            // see AutoImportProjectTracker.scheduleDelayedSmartProjectReload
            Thread.sleep(5L * MERING_SPAN)

            markDirty()
            waitForAllProjectActivities {
              scheduleProjectReload()
            }
          }
        }
        assertStateAndReset(numReload = 1, notified = false, event = "merged project reload")
      }
    }
  }

  fun `test merge sequent project reloads from explicit reload`() {
    test {
      enableAsyncExecution()
      setDispatcherMergingSpan(MERING_SPAN)

      repeat(TEST_ATTEMPTS) {
        parallel {
          repeat(50) { index ->
            thread {

              // see AutoImportProjectTracker.scheduleDelayedSmartProjectReload
              Thread.sleep(MERING_SPAN / 2L * index)

              markDirty()
              waitForAllProjectActivities {
                scheduleProjectReload()
              }
            }
          }
        }
        assertStateAndReset(numReload = 1, notified = false, event = "merged project reload")
      }
    }
  }

  fun `test force sync action during sync`() {
    test {
      enableAsyncExecution()
      setDispatcherMergingSpan(MERING_SPAN)

      repeat(TEST_ATTEMPTS) {
        val expectedRefreshes = 10
        val latch = CountDownLatch(expectedRefreshes)
        whenReloading(expectedRefreshes) {
          latch.countDown()
          latch.await()
        }
        whenReloadStarted(expectedRefreshes - 1) {
          forceReloadProject()
        }
        waitForAllProjectActivities {
          forceReloadProject()
        }
        assertStateAndReset(numReload = expectedRefreshes, notified = false, event = "reloads")
      }
    }
  }

  /**
   * The current implementation of settings files watcher cannot 100% separate cases by the file change time (before or during sync).
   * Decided that all files that changed in the begging of sync are changed before sync.
   * It means that files don't affect the auto-sync project status.
   * (These modifications ignored)
   */
  fun `_test settings file modification during sync`() {
    test { settingsFile ->
      enableAsyncExecution()
      setDispatcherMergingSpan(MERING_SPAN)

      repeat(TEST_ATTEMPTS) {
        val expectedRefreshes = 10
        whenReloading(expectedRefreshes - 1) {
          settingsFile.modify(EXTERNAL)
        }
        waitForAllProjectActivities {
          settingsFile.modify(EXTERNAL)
        }
        assertStateAndReset(numReload = expectedRefreshes, notified = false, event = "reloads")
      }
    }
  }
}