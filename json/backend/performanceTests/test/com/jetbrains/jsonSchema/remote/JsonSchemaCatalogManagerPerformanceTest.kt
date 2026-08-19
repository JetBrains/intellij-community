// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.jetbrains.jsonSchema.JsonSchemaHeavyAbstractTest
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import org.junit.Assert

@PerformanceUnitTest
class JsonSchemaCatalogManagerPerformanceTest : BasePlatformTestCase() {
  private lateinit var catalogManager: JsonSchemaCatalogManager

  override fun setUp() {
    super.setUp()
    catalogManager = configureCatalog()
  }

  override fun getTestDataPath(): String {
    return JsonSchemaHeavyAbstractTest.getJsonSchemaTestDataFilePath("schemaStore/")
  }

  fun testPerformance() {
    val file = myFixture.addFileToProject("some/unknown.json", "").virtualFile
    val schemaFile = catalogManager.getSchemaFileForFile(file)
    Assert.assertNull(schemaFile)
    Benchmark.newBenchmark(getTestName(false)) {
      repeat(1000000) {
        val result = catalogManager.getSchemaFileForFile(file)
        Assert.assertNull(result)
      }
    }.start()
  }

  private fun configureCatalog(): JsonSchemaCatalogManager {
    val catalogManager = JsonSchemaService.Impl.get(project).catalogManager
    val path = JsonSchemaHeavyAbstractTest.getJsonSchemaTestDataFilePath("schemaStore/catalog.json")
    val catalogFile = LocalFileSystem.getInstance().findFileByPath(path)
    Assert.assertNotNull(catalogFile)
    catalogManager.registerTestSchemaStoreFile(catalogFile!!, testRootDisposable)
    return catalogManager
  }
}
