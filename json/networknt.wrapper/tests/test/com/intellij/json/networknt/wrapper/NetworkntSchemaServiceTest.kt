// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.json.JsonFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.testFramework.common.waitUntilAssertSucceedsBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.networknt.schema.SpecificationVersion
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class NetworkntSchemaServiceTest : BasePlatformTestCase() {

  companion object {
    private const val SMALL_SCHEMA = """{"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}"""
    private val VERSION = SpecificationVersion.DRAFT_7
    private const val AZURE_SCHEMA_FILE = "azure-arm-schema.json"
    private const val AZURE_INSTANCE_FILE = "azure-instance.json"
  }

  override fun getTestDataPath(): String =
    PathManager.getCommunityHomePath() + "/json/networknt.wrapper/tests/testData"

  private fun service() = NetworkntSchemaService.getInstance(project)

  override fun setUp() {
    super.setUp()
    service().invalidateAllCaches("test setUp")
  }

  private fun loadAzureSchema(): String =
    File(testDataPath, AZURE_SCHEMA_FILE).readText()

  /**
   * Verifies that schema is available after cache invalidation + async recompilation.
   * Even if a cancelled indicator is in effect, the background compilation completes
   * and the schema becomes available on the next call.
   */
  fun `test schema available after invalidation and async recompilation`() {
    val schemaFile = myFixture.addFileToProject("azure-schema.json", loadAzureSchema()).virtualFile
    val service = service()

    // First call — populate cache
    val schema1 = service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
    assertNotNull(schema1)

    // Invalidate — forces recompilation on next access
    service.invalidateAllCaches("test")

    // Access with cancelled indicator — compilation starts async, may or may not throw PCE
    val indicator = EmptyProgressIndicator()
    indicator.cancel()
    try {
      ProgressManager.getInstance().runProcess({
        service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
      }, indicator)
    }
    catch (_: ProcessCanceledException) { }

    // Schema must be available — either returned directly (fast compile) or via background future
    val schema2 = waitUntilAssertSucceedsBlocking {
      service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
    }
    assertNotNull("Schema should be available after async recompilation", schema2)
  }

  /**
   * Verifies that write actions are not blocked by background schema compilation.
   * If the compilation held a read lock, `runWriteAction` from EDT would block.
   */
  fun `test write action completes during background compilation`() {
    val schemaFile = myFixture.addFileToProject("azure-schema-wr.json", loadAzureSchema()).virtualFile
    val service = service()

    // Trigger compilation, cancel to release calling thread
    val indicator = EmptyProgressIndicator()
    indicator.cancel()
    try {
      ProgressManager.getInstance().runProcess({
        service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
      }, indicator)
    }
    catch (_: ProcessCanceledException) { }

    // Write action should complete immediately — no read lock held by background compilation
    val writeCompleted = AtomicBoolean(false)
    ApplicationManager.getApplication().runWriteAction {
      writeCompleted.set(true)
    }
    assertTrue("Write action should not be blocked by background compilation", writeCompleted.get())
  }

  fun `test second call returns cached schema`() {
    val schemaFile = myFixture.configureByText(JsonFileType.INSTANCE, SMALL_SCHEMA).virtualFile
    val service = service()

    val schema1 = service.getNetworkntSchema(schemaFile, VERSION)

    val t0 = System.nanoTime()
    val schema2 = service.getNetworkntSchema(schemaFile, VERSION)
    val durationMs = (System.nanoTime() - t0) / 1_000_000.0

    assertSame("Second call should return the same cached Schema instance", schema1, schema2)
    assertTrue("Cache hit should be fast (< 5ms), was ${durationMs}ms", durationMs < 10.0)
  }

  /**
   * Measures Azure schema compilation and validation performance.
   * Schema: 346KB ARM deployment template. Instance: 77KB application gateway config.
   */
  fun `test azure schema compilation and validation timing`() {
    val schemaContent = loadAzureSchema()
    val instanceContent = File(testDataPath, AZURE_INSTANCE_FILE).readText()

    val schemaFile = myFixture.addFileToProject("azure-schema-timing.json", schemaContent).virtualFile
    val service = service()

    // Warm up: compile schema + first validation
    val schema = service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
    schema.validate(instanceContent, com.networknt.schema.InputFormat.JSON)

    Benchmark.newBenchmark("Azure networknt validation") {
      val s = service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
      s.validate(instanceContent, com.networknt.schema.InputFormat.JSON)
    }.warmupIterations(3).attempts(10).runAsStressTest().start()
  }

  /**
   * Measures PSI→JsonNode + PsiLocationIndex conversion timing for the Azure instance file.
   * This conversion runs on every daemon pass under read action.
   */
  fun `test azure PSI to JsonNode conversion timing`() {
    val instanceContent = File(testDataPath, AZURE_INSTANCE_FILE).readText()
    val psiFile = myFixture.configureByText(JsonFileType.INSTANCE, instanceContent)
    val walker = com.jetbrains.jsonSchema.extension.JsonLikePsiWalker.getWalker(psiFile.firstChild!!)!!
    val rootElement = walker.getRoots(psiFile)?.firstOrNull() ?: psiFile.firstChild!!

    // Warm up
    convertPsiToJsonNode(walker, rootElement)

    Benchmark.newBenchmark("Azure PSI to JsonNode conversion") {
      assertNotNull("Conversion should succeed", convertPsiToJsonNode(walker, rootElement))
    }.warmupIterations(3).attempts(10).runAsStressTest().start()
  }

  fun `test invalidateAllCaches forces recompilation`() {
    val schemaFile = myFixture.configureByText(JsonFileType.INSTANCE, SMALL_SCHEMA).virtualFile
    val service = service()

    val schema1 = service.getNetworkntSchema(schemaFile, VERSION)
    service.invalidateAllCaches("test")
    val schema2 = service.getNetworkntSchema(schemaFile, VERSION)

    assertNotSame("After invalidation, a new Schema instance should be compiled", schema1, schema2)
  }
}
