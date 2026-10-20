// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.JSON_SCHEMA_CHANGED
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class DownloadedSchemaInvalidationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val project get() = projectFixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun testASuccessfulDownloadResetsTheSchemaService() {
    assertResetsOnce(SchemaUrl.parse("https://example.com/schema.json"))
  }

  @Test
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
    project.messageBus.connect(disposable)
      .subscribe(JSON_SCHEMA_CHANGED, Runnable { changed.countDown() })
    try {
      project.messageBus.syncPublisher(RemoteSchemaDownloadListener.TOPIC).downloadFinished(url.value)

      assertTrue(changed.await(10, TimeUnit.SECONDS), "the download consumer never completed a batch")
      assertEquals(1, resets.get())
    }
    finally {
      schemaService.unregisterResetAction(action)
    }
  }
}
