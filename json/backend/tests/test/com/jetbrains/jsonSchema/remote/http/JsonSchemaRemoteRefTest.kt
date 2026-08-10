// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.waitUntilAssertSucceedsBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Integration test verifying the two-phase highlighting flow for remote `$ref` resolution.
 *
 * [JsonSchemaRemoteContentService.getInstance] is replaced with a new instance that uses the registered
 * [JsonSchemaFileCache] and [JsonSchemaHttpClient] services.
 * The test sets the [com.jetbrains.jsonSchema.remote.JsonFileResolver.REMOTE_ENABLED_IN_TESTS] test mode flag,
 * because [com.jetbrains.jsonSchema.remote.JsonFileResolver.isRemoteEnabled] returns `false` in unit test mode without it.
 * A private [LightProjectDescriptor] keeps this test's project from being shared with
 * other tests, so `NetworkntSchemaService`/`JsonSchemaUpdater` are guaranteed to register their
 * download listener against the very instance replaced in [setUp], not a pre-existing real singleton.
 *
 * Phase 1: the remote schema referenced by `$ref` is not cached. [com.intellij.json.networknt.wrapper.IntelliJSchemaLoader]'s
 * resource loader returns `null` for the unresolved `$ref`, networknt treats it as unresolved, and highlighting reports no errors.
 *
 * Phase 2: the async download (triggered by the same resolver's cache-miss `prefetch`) completes, the
 * download listener registered by `NetworkntSchemaService`/`JsonSchemaUpdater` invalidates caches and
 * restarts the daemon for open editors. A fresh pass resolves the `$ref` from cache and reports the type mismatch.
 */
class JsonSchemaRemoteRefTest : BasePlatformTestCase() {

  private object IsolatedProjectDescriptor : LightProjectDescriptor()

  override fun getProjectDescriptor(): LightProjectDescriptor = IsolatedProjectDescriptor

  private lateinit var scope: CoroutineScope
  private lateinit var httpServer: HttpServer
  private lateinit var testAnalyzer: TestDaemonCodeAnalyzerImpl
  private lateinit var daemonAnalyzer: DaemonCodeAnalyzerImpl
  private var baseUrl: String = ""

  override fun setUp() {
    super.setUp()

    httpServer = HttpServer.create(InetSocketAddress(0), 0)
    httpServer.executor = Executors.newCachedThreadPool()
    baseUrl = "http://localhost:${httpServer.address.port}"

    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    clearRemoteSchemaCache()
    setRemoteSchemaAccessAllowed(true, testRootDisposable)
    replaceRemoteSchemaServices(project, scope, testRootDisposable)

    TrustedProjects.setProjectTrusted(project, true)
    myFixture.enableInspections(JsonSchemaComplianceInspection::class.java)
    assertTrue(Registry.`is`("json.schema.use.networknt.validation"))

    testAnalyzer = TestDaemonCodeAnalyzerImpl(project)
    testAnalyzer.prepareForTest()
    daemonAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
    daemonAnalyzer.setUpdateByTimerEnabled(true)
  }

  override fun tearDown() {
    try {
      daemonAnalyzer.setUpdateByTimerEnabled(false)
      httpServer.stop(0)
      scope.cancel()
      clearRemoteSchemaCache()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testTwoPhaseHighlighting() {
    val serverGate = CompletableFuture<Unit>()

    httpServer.createContext("/type-schema.json") { exchange ->
      serverGate.get()
      val body = """{"type": "string"}""".toByteArray(Charsets.UTF_8)
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    httpServer.start()

    val rootSchema = """{"${'$'}ref": "$baseUrl/type-schema.json"}"""
    registerSchema(rootSchema)

    val psiFile = myFixture.configureByText("test.json", "42")

    // Phase 1: remote schema is not cached — $ref is unresolved, no schema warnings expected.
    // The resource loader's cache-miss path fires a background prefetch that blocks on serverGate.
    val phase1 = schemaHighlights(testAnalyzer.waitHighlighting(psiFile, HighlightSeverity.WARNING))
    assertTrue(
      "Phase 1: expected no schema warnings while remote schema is not yet downloaded, but got: $phase1",
      phase1.isEmpty(),
    )

    // Unblock the HTTP server → prefetch completes → download listener fires →
    // invalidateAllCaches() + JSON_SCHEMA_CHANGED + DaemonCodeAnalyzer restart for open editors.
    serverGate.complete(Unit)

    // Phase 2: wait until the daemon auto-restarts and produces the type mismatch warning.
    waitUntilAssertSucceedsBlocking {
      val phase2 = schemaHighlights(testAnalyzer.waitHighlighting(psiFile, HighlightSeverity.WARNING))
      assertFalse(
        "Phase 2: expected a type mismatch warning after the remote schema download, but got none",
        phase2.isEmpty(),
      )
      assertTrue(
        "Phase 2: expected a 'type' warning, but got: ${phase2.map { it.description }}",
        phase2.any { it.description?.contains("type", ignoreCase = true) == true },
      )
    }
  }

  // region helpers

  private fun schemaHighlights(highlights: List<HighlightInfo>): List<HighlightInfo> =
    highlights.filter { it.inspectionToolId == "JsonSchemaCompliance" }

  private fun registerSchema(schemaText: String) {
    JsonSchemaHighlightingTestBase.registerJsonSchema(myFixture, schemaText, "json") { true }
  }

  // endregion
}
