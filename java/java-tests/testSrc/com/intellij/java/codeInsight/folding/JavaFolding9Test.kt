/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.folding

import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat

class JavaFolding9Test : JavaFoldingTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  fun testModule() {
    val text = """
        /**
         * A lonely comment.
         */
        @Deprecated
        @SuppressWarnings("unused")
        module M {
          requires java.base;
        }""".trimIndent()
    configure(text)

    val regions = myFixture.editor.foldingModel.allFoldRegions
    assertThat(regions).hasSize(4)

    assertThat(regions.map { it.startOffset to it.endOffset }).containsExactly(
      range(text, "/**", "*/"),
      range(text, "@D", ")"),
      range(text, "@S", ")"),
      range(text, "{", "}"))

    val region = regions.last()
    assertThat(region.isExpanded).isTrue()
    assertThat(region.placeholderText).isEqualTo("{...}")
  }

  private fun range(text: String, start: String, end: String) =
    text.indexOf(start) to text.indexOf(end) + end.length
}