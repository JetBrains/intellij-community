// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Test

@TestApplication
internal class AsyncRenderingTest {
  /**
   * Regression test for IJPL-246889: [LookupElement.getExpensiveRenderer] must be invoked under a read action.
   */
  @Test
  fun `getExpensiveRenderer is invoked under a read action`() = timeoutRunBlocking<Unit> {
    val readAccessAllowed = CompletableDeferred<Boolean>()

    val element = object : LookupElement() {
      override fun getLookupString(): String = "test"

      override fun getExpensiveRenderer(): LookupElementRenderer<out LookupElement>? {
        readAccessAllowed.complete(ApplicationManager.getApplication().isReadAccessAllowed)
        // Returning null is enough: scheduleRendering bails out right after the renderer lookup,
        // so the test does not need valid PSI/editor or a real renderer.
        return null
      }
    }

    val rendering = AsyncRendering(coroutineScope = this, renderingCallback = { _, _ -> })
    rendering.scheduleRendering(element)

    assertThat(readAccessAllowed.await()).isTrue()
  }
}
