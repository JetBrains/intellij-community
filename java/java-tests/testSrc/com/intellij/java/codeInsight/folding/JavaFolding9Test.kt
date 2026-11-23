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

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class JavaFolding9Test {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9)
  }

  private val folding by javaFoldingFixture(projectFixture)

  @Test
  fun testModule(): Unit = timeoutRunBlocking {
    val text = """
        /**
         * A lonely comment.
         */
        @Deprecated
        @SuppressWarnings("unused")
        module M {
          requires java.base;
        }""".trimIndent()
    folding.configure(text)

    readAction {
      val regions = folding.fixture.editor.foldingModel.allFoldRegions
      assertThat(regions).hasSize(3)

      assertThat(regions.map { it.startOffset to it.endOffset }).containsExactly(
        range(text, "/**", "*/"),
        range(text, "@S", ")"),
        range(text, "{", "}"))

      val region = regions.last()
      assertThat(region.isExpanded).isTrue()
      assertThat(region.placeholderText).isEqualTo("{...}")
    }
  }

  private fun range(text: String, start: String, end: String) =
    text.indexOf(start) to text.indexOf(end) + end.length
}