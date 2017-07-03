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
package com.intellij.java.codeInsight.completion

import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import org.assertj.core.api.Assertions.assertThat

class ModuleCompletionTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    addFile("module-info.java", "module M2 { }", M2)
  }

  fun testFileHeader1() = variants("<caret>", "module", "open")
  fun testFileHeader2() = complete("open <caret>", "open module <caret>")
  fun testModuleName() = variants("module M<caret>")
  fun testStatements1() = variants("module M { <caret> }", "requires", "exports", "opens", "uses", "provides")
  fun testStatements2() = complete("module M { requires X; ex<caret> }", "module M { requires X; exports <caret> }")

  fun testRequires() {
    variants("module M { requires <caret>", "transitive", "static", "M2", "java.base", "lib.multi.release", "lib.named")
    complete("module M { requires t<caret> }", "module M { requires transitive <caret> }")
    complete("module M { requires M<caret> }", "module M { requires M2;<caret> }")
  }

  fun testExports() {
    addFile("pkg/empty/package-info.java", "package pkg.empty;")
    addFile("pkg/main/C.java", "package pkg.main;\nclass C { }")
    addFile("pkg/other/C.groovy", "package pkg.other\nclass C { }")
    variants("module M { exports pkg.<caret> }", "main", "other")
    complete("module M { exports pkg.o<caret> }", "module M { exports pkg.other;<caret> }")
  }

  fun testUses() {
    addFile("pkg/main/MySvc.java", "package pkg.main;\npublic class MySvc { }")
    addFile("pkg/main/MySvcImpl.java", "package pkg.main;\nclass MySvcImpl extends MySvc { }")
    complete("module M { uses MyS<caret> }", "module M { uses pkg.main.MySvc;<caret> }")
  }

  fun testProvides() {
    addFile("pkg/main/MySvc.java", "package pkg.main;\npublic class MySvc { }")
    addFile("pkg/main/MySvcImpl.java", "package pkg.main;\nclass MySvcImpl extends MySvc { }")
    complete("module M { provides MyS<caret> }", "module M { provides pkg.main.MySvc <caret> }")
    complete("module M { provides pkg.main.MySvc <caret> }", "module M { provides pkg.main.MySvc with <caret> }")
    complete("module M { provides pkg.main.MySvc with MSI<caret> }", "module M { provides pkg.main.MySvc with pkg.main.MySvcImpl;<caret> }")
  }

  fun testImports() {
    addFile("module-info.java", "module M { requires M2; }")
    addFile("module-info.java", "module M2 { exports pkg.m2; }", M2)
    addFile("pkg/m2/C2.java", "package pkg.m2;\npublic class C2 { }", M2)
    addFile("pkg/m2/impl/C2Impl.java", "package pkg.m2.impl;\npublic class C2Impl { }", M2)
    myFixture.configureByText("test.java", "import pkg.m2.<caret>")
    myFixture.completeBasic()
    assertOrderedEquals(myFixture.lookupElementStrings!!, "*", "C2") // no 'impl'
  }

  //<editor-fold desc="Helpers.">
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
  //</editor-fold>
}