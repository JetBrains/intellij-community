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
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase

class JavaModuleNamingTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(JavaModuleNamingInspection())
  }

  fun testSimple() = highlighting("""module <warning descr="Module name component 'foo1' should avoid terminal digits">foo1</warning>.bar { }""")
  fun testDevanagariDigitsAreBannedToo() = highlighting("""module <warning descr="Module name component 'foo१' should avoid terminal digits">foo१</warning>.bar { }""")
  fun testMiddleDigitsAllowed() = highlighting("""module f0o.b4r { }""")

  fun testFix() {
    myFixture.configureByText("module-info.java", "module <caret>f0o123.b4r१ { }")
    myFixture.launchAction(myFixture.findSingleIntention("Rename"))
    myFixture.checkResult("module-info.java", "module f0o.b4r { }", false)
  }

  private fun highlighting(text: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.checkHighlighting()
  }
}