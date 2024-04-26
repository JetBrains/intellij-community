// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IndexingUtilsTest {
  @Test
  fun testIdentifiersSplitting() {
    val cases = listOf(
      "lowercase" to "lowercase",
      "Uppercase" to "Uppercase",
      "CamelCase" to "Camel Case",
      "snake_case" to "snake case",
      "Mixed_caseString" to "Mixed case String",
      "spaces in identifier" to "spaces in identifier",
      "HTML" to "HTML",
      "PDFLoader" to "PDF Loader",
      "AString" to "A String",
      "SimpleXMLParser" to "Simple XML Parser",
      "GL11Version" to "GL 11 Version",
      "русскоеСлово" to "русское Слово"
    )

    for ((input, expected) in cases) {
      assertEquals(expected.lowercase(), splitIdentifierIntoTokens(input))
      assertEquals(expected, splitIdentifierIntoTokens(input, lowercase = false))
    }
  }
}