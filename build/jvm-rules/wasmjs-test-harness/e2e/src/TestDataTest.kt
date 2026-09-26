// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package e2e

import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDataTest {
  @Test
  fun readsPackageRelativeTestData(): Promise<JsAny?> =
    fetchText("testdata/sample.txt").then { text ->
      assertEquals("hello from testdata", text.toString().trim())
      null
    }
}

private fun fetchText(url: String): Promise<JsString> = js("fetch(url).then((response) => response.text())")
