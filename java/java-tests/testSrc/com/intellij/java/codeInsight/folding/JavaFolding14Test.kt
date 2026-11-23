// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.folding

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class JavaFolding14Test {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val folding by javaFoldingFixture(projectFixture)

  @Test
  fun testTextBlock(): Unit = timeoutRunBlocking {
    val text = """
      String x = ""${'"'}
        @InProceedings{6055279,  Title                    = {Educational session 1},
          Booktitle                = {Custom Integrated Circuits Conference (CICC), 2011 IEEE},
          Year                     = {2011},
          Month                    = {Sept},
        ""${'"'};
    """.trimIndent()
    folding.configure(text)

    val regions = readAction { folding.fixture.editor.foldingModel.allFoldRegions.joinToString("\n") }
    assertEquals(
      "FoldRegion -(11:267), placeholder='\"\"${'"'}@InProceedings{6055279,  Title                    = {Educational session 1},...'",
      regions)
  }

  @Test
  fun testRecord(): Unit = timeoutRunBlocking {
    val text = """
        class B {}
        
        record A(
          String s, 
          int a
        ) {
          A {
            f();
          }
          void f() {}
        }""".trimIndent()
    folding.configure(text)

    val regions = readAction { folding.fixture.editor.foldingModel.allFoldRegions.joinToString("\n") }
    assertEquals("""
        FoldRegion -(20:44), placeholder='(...)'
        FoldRegion -(45:81), placeholder='{...}'
        FoldRegion -(51:65), placeholder='{...}'
    """.trimIndent(), regions)
  }
}