// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.impl.storage.durablemap.database.DurableDatabaseIndexLayoutProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
internal class IndexMaintenanceServiceTest {
  @Test
  fun `closing an old registration twice preserves the next provider`() = runTest {
    val service = IndexMaintenanceService(backgroundScope)
    val oldProvider = provider()
    val oldRegistration = service.registerForMaintenance(oldProvider)
    assertTrue(service.hasActiveProvider)
    assertThrows(IllegalStateException::class.java) { service.registerForMaintenance(oldProvider) }
    assertThrows(IllegalStateException::class.java) { service.registerForMaintenance(provider()) }

    oldRegistration.close()
    assertFalse(service.hasActiveProvider)

    val nextRegistration = service.registerForMaintenance(provider())
    oldRegistration.close()
    assertTrue(service.hasActiveProvider, "A stale handle must not remove the next provider")

    nextRegistration.close()
    assertFalse(service.hasActiveProvider)
  }

  @Test
  fun `replacement during maintenance permits a later cycle`() = runTest {
    val service = IndexMaintenanceService(backgroundScope)
    var registration = service.registerForMaintenance(provider())

    assertTrue(service.tryRunMaintenance {
      registration.close()
      assertFalse(service.hasActiveProvider)
      assertFalse(service.tryRunMaintenance { error("A cycle is already running") })
      registration = service.registerForMaintenance(provider())
    })

    assertTrue(service.hasActiveProvider)
    assertTrue(service.tryRunMaintenance { }, "The replacement provider must permit the next cycle")
    registration.close()
  }

  @Test
  fun `a replaced provider cannot start a cycle based on old metrics`() = runTest {
    val service = IndexMaintenanceService(backgroundScope)
    val oldProvider = provider()
    service.registerForMaintenance(oldProvider).close()
    service.registerForMaintenance(provider()).use {
      assertFalse(service.tryRunMaintenance(oldProvider) { error("The old provider is closed") })
    }
  }

  @Test
  fun `maintenance waits for growth but continues useful evacuation`() {
    val baseline = metrics(allocated = 10)
    assertFalse(shouldRunMaintenance(metrics(sealedChunks = 0), null), "There is no chunk to compact")
    assertTrue(shouldRunMaintenance(metrics(), null), "The first eligible cycle has no baseline")
    assertFalse(shouldRunMaintenance(metrics(allocated = 19), baseline), "Nine new blocks are below 10% of the baseline")
    assertTrue(shouldRunMaintenance(metrics(allocated = 20), baseline), "Ten new blocks reach the threshold")
    assertTrue(shouldRunMaintenance(metrics(retired = 10), baseline), "Retiring old blocks also creates free space")
    assertTrue(shouldRunMaintenance(metrics(createdChunks = 1), baseline), "A new chunk can contain large blocks")
    assertTrue(shouldRunMaintenance(metrics(retiredChunks = 1), baseline), "A retired chunk can be deleted on reopen")
    assertTrue(shouldRunMaintenance(metrics(), metrics(evacuated = 1)), "An unfinished evacuation needs another pass")
  }

  @Test
  fun `failed maintenance releases its hold`() = runTest {
    val service = IndexMaintenanceService(backgroundScope)
    val registration = service.registerForMaintenance(provider())

    assertThrows(IllegalStateException::class.java) {
      service.tryRunMaintenance {
        registration.close()
        error("Failed to reopen the database")
      }
    }
    assertFalse(service.hasActiveProvider)
    assertFalse(service.tryRunMaintenance { error("No provider is registered") })

    service.registerForMaintenance(provider()).use {
      assertTrue(service.tryRunMaintenance { }, "A later provider must permit maintenance again")
    }
  }

  private fun provider(): DurableDatabaseIndexLayoutProvider =
    DurableDatabaseIndexLayoutProvider(databasePath = Path.of("unused-maintenance-test-database"))

  private fun metrics(
    sealedChunks: Int = 1,
    retiredChunks: Int = 0,
    allocated: Long = 0,
    retired: Long = 0,
    createdChunks: Long = 0,
    evacuated: Long = 0,
  ): MaintenanceMetrics = MaintenanceMetrics(
    sealedChunks = sealedChunks,
    retiredChunks = retiredChunks,
    totalBlocks = 100,
    allocatedBlocks = allocated,
    retiredBlocks = retired,
    createdChunks = createdChunks,
    evacuatedBlocks = evacuated,
  )
}
