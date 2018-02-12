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

import com.intellij.codeInspection.java19modules.JavaRequiresAutoModuleInspection
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase

class JavaRequiresAutoModuleInspectionTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  private lateinit var inspection: JavaRequiresAutoModuleInspection

  override fun setUp() {
    super.setUp()
    inspection = JavaRequiresAutoModuleInspection()
    myFixture.enableInspections(inspection)
  }

  fun testTransitive() {
    highlighting("""module M { requires transitive <warning descr="'requires transitive' directive for an automatic module">lib.claimed</warning>; }""")
  }

  fun testAny() {
    inspection.TRANSITIVE_ONLY = false
    highlighting("""module M { requires <warning descr="'requires' directive for an automatic module">lib.claimed</warning>; }""")
  }

  private fun highlighting(text: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.checkHighlighting()
  }
}