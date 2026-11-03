// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.folding

class JavaFolding14Test : JavaFoldingTestCase() {

  fun testTextBlock() {
    val text = """
      String x = ""${'"'}
        @InProceedings{6055279,  Title                    = {Educational session 1},
          Booktitle                = {Custom Integrated Circuits Conference (CICC), 2011 IEEE},
          Year                     = {2011},
          Month                    = {Sept},
        ""${'"'};
    """.trimIndent()
    configure(text)

    val regions = myFixture.editor.foldingModel.allFoldRegions
    assertEquals(
      "FoldRegion -(11:267), placeholder='\"\"${'"'}@InProceedings{6055279,  Title                    = {Educational session 1},...'",
      regions.joinToString("\n"))
  }

  fun testRecord() {
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
    configure(text)

    val regions = myFixture.editor.foldingModel.allFoldRegions
    assertEquals("""
        FoldRegion -(20:44), placeholder='(...)'
        FoldRegion -(45:81), placeholder='{...}'
        FoldRegion -(51:65), placeholder='{...}'
    """.trimIndent(), regions.joinToString("\n"))
  }
}