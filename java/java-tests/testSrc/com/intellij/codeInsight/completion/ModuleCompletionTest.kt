/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion

import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat

class ModuleCompletionTest : LightFixtureCompletionTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  fun testFileHeader() = complete("<caret>", "module <caret>")
  fun testStatements1() = variants("module M { <caret> }", "requires", "exports", "uses", "provides")
  fun testStatements2() = complete("module M { requires X; ex<caret> }", "module M { requires X; exports <caret> }")

  private fun complete(text: String, expected: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.completeBasic()
    myFixture.checkResult(expected)
  }

  private fun variants(text: String, vararg variants: String) {
    myFixture.configureByText("module-info.java", text)
    val result = myFixture.completeBasic()?.map { it.lookupString }
    assertThat(result).containsExactlyInAnyOrder(*variants)
  }
}