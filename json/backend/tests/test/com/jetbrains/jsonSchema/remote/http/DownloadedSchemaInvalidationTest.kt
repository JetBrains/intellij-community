// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.JSON_SCHEMA_CHANGED
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DownloadedSchemaInvalidationTest : BasePlatformTestCase() {

  fun testASuccessfulDownloadResetsTheSchemaService() {
    assertResetsOnce(SchemaUrl.parse("https://example.com/schema.json"))
  }

  fun testCatalogDownloadResetsOnceNotTwice() {
    // the catalog's own listener used to reset independently of the batched consumer
    assertResetsOnce(SchemaUrl.parse("https://schemastore.org/api/json/catalog.json"))
  }

  private fun assertResetsOnce(url: SchemaUrl) {
    val resets = AtomicInteger()
    val changed = CountDownLatch(1)
    val schemaService = JsonSchemaService.Impl.get(project)
    val action = Runnable { resets.incrementAndGet() }
    schemaService.registerResetAction(action)
    project.messageBus.connect(testRootDisposable)
      .subscribe(JSON_SCHEMA_CHANGED, Runnable { changed.countDown() })
    try {
      project.messageBus.syncPublisher(RemoteSchemaDownloadListener.TOPIC).downloadFinished(url.value)

      assertTrue("the download consumer never completed a batch", changed.await(10, TimeUnit.SECONDS))
      assertEquals(1, resets.get())
    }
    finally {
      schemaService.unregisterResetAction(action)
    }
  }
}
