// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaDeprecationInspection
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaRefReferenceInspection
import java.io.File

/**
 * Runs Azure ARM performance benchmarks with networknt validation enabled.
 *
 * Inherits all tests from [JsonSchemaPerformanceTest]; overrides the Azure tests
 * to use `doHighlighting()` instead of `checkHighlighting()` because networknt
 * produces "Does not match any of the allowed schemas" warnings via [getRawErrorCap][com.intellij.json.networknt.wrapper.NetworkntErrorMapper.getRawErrorCap].
 *
 * This gives side-by-side perf comparison: run the parent for old IJ validator metrics,
 * run this class for networknt metrics.
 */
@PerformanceUnitTest
class JsonSchemaPerformanceNetworkntTest : JsonSchemaPerformanceTest() {

  override fun setUp() {
    super.setUp()
    // Override parent's networknt=false with true for A/B comparison
    Registry.get("json.schema.use.networknt.validation").setValue(true, testRootDisposable)
    Registry.get("json.schema.networknt.resolve.remote.refs").setValue(false, testRootDisposable)
    disableRemoteActivity()
  }

  private fun disableRemoteActivity() {
    val config = JsonSchemaCatalogProjectConfiguration.getInstance(project)
    val wasCatalogEnabled = config.isCatalogEnabled
    val wasRemoteEnabled = config.isRemoteActivityEnabled
    val wasPreferRemote = config.isPreferRemoteSchemas
    config.setState(true, false, false)
    Disposer.register(testRootDisposable) {
      config.setState(wasCatalogEnabled, wasRemoteEnabled, wasPreferRemote)
    }
  }

  override fun testAzureHighlightingAgainstNewSchemaImplementation() {
    doTestAzurePerformanceNetworknt(true)
  }

  override fun testAzureHighlightingAgainstOldSchemaImplementation() {
    doTestAzurePerformanceNetworknt(false)
  }

  override fun testAzureRehighlightAfterTyping() {
    myFixture.enableInspections(JsonSchemaComplianceInspection::class.java)
    val schemaText = FileUtil.loadFile(File(testDataPath + "/azure-schema.json"))
    JsonSchemaHighlightingTestBase.registerJsonSchema(myFixture, schemaText, "json") { true }
    myFixture.configureByFile("/azure-file.json")

    // warmup
    myFixture.doHighlighting()

    Benchmark.newBenchmark("Azure rehighlight after typing (networknt)") {
      WriteCommandAction.runWriteCommandAction(project) {
        myFixture.editor.document.insertString(1, "x")
      }
      myFixture.doHighlighting()
      WriteCommandAction.runWriteCommandAction(project) {
        myFixture.editor.document.deleteString(1, 2)
      }
      myFixture.doHighlighting()
    }.warmupIterations(10).attempts(50).start()
  }

  private fun doTestAzurePerformanceNetworknt(useNewImplementation: Boolean) {
    Registry.get("json.schema.object.v2").setValue(useNewImplementation)
    Benchmark.newBenchmark("Highlight azure json by schema (networknt)") {
      myFixture.enableInspections(JsonSchemaComplianceInspection::class.java)
      myFixture.enableInspections(JsonSchemaRefReferenceInspection::class.java)
      myFixture.enableInspections(JsonSchemaDeprecationInspection::class.java)
      val schemaText = FileUtil.loadFile(File(testDataPath + "/azure-schema.json"))
      JsonSchemaHighlightingTestBase.registerJsonSchema(myFixture, schemaText, "json") { true }
      myFixture.configureByFile("/azure-file.json")
      val highlights = myFixture.doHighlighting()
      val warnings = highlights.filter { it.severity.name == "WARNING" }
      println("  [networknt] v2=$useNewImplementation: ${highlights.size} highlights, ${warnings.size} warnings")
    }.attempts(5).start()
  }
}
