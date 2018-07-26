// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.folding

import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat

class JavaFolding10Test : JavaFoldingTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_10

  fun testLocalVariable() {
    val text = """
        class A {
          void test() {
            var x = "FOO";
          }
        }""".trimIndent()
    configure(text)

    val regions = myFixture.editor.foldingModel.allFoldRegions
    assertThat(regions).hasSize(3)

    val region = regions[1]
    assertThat(region.placeholderText).isEqualTo("String")
  }
}