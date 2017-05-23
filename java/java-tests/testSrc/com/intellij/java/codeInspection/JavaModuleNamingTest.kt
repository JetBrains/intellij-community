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
package com.intellij.java.codeInspection

import com.intellij.codeInspection.java19modules.JavaModuleNamingInspection
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase

class JavaModuleNamingTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(JavaModuleNamingInspection())
  }

  fun testTerminalDigits() {
    highlighting("""module <warning descr="Module name 'foo.bar42' should avoid terminal digits">foo.bar42</warning> { }""")
    fix("module <caret>foo.bar42 { }", "module foo.bar { }")
  }

  fun testTerminalDigitsAndMiddleComments() {
    highlighting("""module <warning descr="Module name 'foo.baz42' should avoid terminal digits">foo/*.bar*/.baz42</warning> { }""")
  }

  private fun highlighting(text: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.checkHighlighting()
  }

  private fun fix(before: String, after: String) {
    myFixture.configureByText("module-info.java", before)
    myFixture.launchAction(myFixture.findSingleIntention("Rename"))
    myFixture.checkResult("module-info.java", after, false)
  }
}