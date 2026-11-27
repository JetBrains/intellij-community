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
package com.intellij.java.psi.formatter.java

import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.formatter.IndentRangesCalculator
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class IndentRangesCalculatorTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)
  
  @Test
  fun `test simple ranges calculation`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
        "test.txt", 
"""class Test {
  void foo() {
    if (1 > 2) {
      int a = 3;
    }
  }
}
""")
    
    val document = myFixture.editor.document
    val calculator = IndentRangesCalculator(document, TextRange(32, 67))
    val ranges = readAction { calculator.calcIndentRanges() }
    
    assertThat(ranges).hasSize(3)
    assertThat(ranges[0]).isEqualTo(TextRange(28, 32))
    assertThat(ranges[1]).isEqualTo(TextRange(45, 51))
    assertThat(ranges[2]).isEqualTo(TextRange(62, 66))
  }
  
}