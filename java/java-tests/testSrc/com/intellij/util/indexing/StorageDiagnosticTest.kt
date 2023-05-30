// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumperUtils
import com.intellij.util.indexing.diagnostic.StorageDiagnosticData
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.PersistentMapBuilder
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.pathString

class StorageDiagnosticTest {
  @get: Rule
  val tempDir: TempDirectory = TempDirectory()

  @get: Rule
  val app: ApplicationRule = ApplicationRule()

  @Test
  fun `test dump persistent map stats`() {
    val dumpFile = tempDir.newFile("storage-data-dump.json").toPath()

    val mapFile = tempDir.newDirectoryPath("map-dir").resolve("map")
    PersistentMapBuilder.newBuilder(mapFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE).build().use { map ->

      map.put("qwerty", "123456")
      map.put("intellij", "idea")
      map.force()

      val stats = StorageDiagnosticData.getStorageDataStatistics()
      IndexDiagnosticDumperUtils.writeValue(dumpFile, stats)
    }

    val diagnosticJson = IndexDiagnosticDumperUtils.jacksonMapper.readTree(dumpFile.toFile())
    val mapStats = diagnosticJson["otherStorageStats"]["statsPerPhm"][mapFile.pathString]
    Assert.assertNotNull(mapStats)
    Assert.assertEquals(1, mapStats["persistentEnumeratorStatistics"]["btreeStatistics"]["pages"].asInt())
    Assert.assertEquals(2, mapStats["persistentEnumeratorStatistics"]["btreeStatistics"]["elements"].asInt())
    Assert.assertEquals(65, mapStats["valueStorageSizeInBytes"].asInt())
  }
}