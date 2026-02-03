// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.agent.extension

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonUtilTest {

  private companion object {
    private val FIRST_JSON = """
    {
      "first.first" : {
        "first.first.first" : [ "hello", "world" ],
        "first.first.second" : [ "how", "is" ],
        "first.first.third" : 32
      },
      "first.second" : {
        "second.second" : [ "it", "going" ]
      },
      "first.third" : "?",
      "first.fourth" : 112233
    }
    """.trimIndent()
    private val SECOND_JSON = """
    {
      "second.first" : {
        "second.first.first" : [ "values", "from", "the", "first", "node", "of", "the", "second", "object" ],
        "second.first.second" : [ "something", "something" ],
        "2" : 4
      },
      "first.first" : {
        "first.first.first" : [ "this", "value", "should", "be", "appended", "to", "the", "existing", "node" ],
        "second.first.second" : [ "this", "value", "should", "be", "appended", "as", "a", "new", "node" ],
        "4" : 2,
        "first.first.third" : "conflicting value"
      }
    }
    """.trimIndent()
    private val EXPECTED_MERGED_JSON = """
    {
      "first.first" : {
        "first.first.first" : [ "hello", "world", "this", "value", "should", "be", "appended", "to", "the", "existing", "node" ],
        "first.first.second" : [ "how", "is" ],
        "first.first.third" : "conflicting value",
        "second.first.second" : [ "this", "value", "should", "be", "appended", "as", "a", "new", "node" ],
        "4" : 2
      },
      "first.second" : {
        "second.second" : [ "it", "going" ]
      },
      "first.third" : "?",
      "first.fourth" : 112233,
      "second.first" : {
        "second.first.first" : [ "values", "from", "the", "first", "node", "of", "the", "second", "object" ],
        "second.first.second" : [ "something", "something" ],
        "2" : 4
      }
    }
    """.trimIndent()
  }

  @Test
  fun `test json merger`() {
    assertEquals(EXPECTED_MERGED_JSON, JsonUtil.merge(FIRST_JSON, SECOND_JSON))
  }
}