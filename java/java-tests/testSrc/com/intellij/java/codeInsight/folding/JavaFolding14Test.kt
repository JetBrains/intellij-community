// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.folding

import com.intellij.testFramework.LightProjectDescriptor

class JavaFolding14Test : JavaFoldingTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_15

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