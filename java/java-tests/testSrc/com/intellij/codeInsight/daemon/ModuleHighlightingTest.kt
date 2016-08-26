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
package com.intellij.codeInsight.daemon

import com.intellij.psi.PsiJavaModule
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.MultiModuleJava9ProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

class ModuleHighlightingTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = MultiModuleJava9ProjectDescriptor

  fun testWrongFileName() {
    myFixture.configureByText("M.java", """/* ... */ <error descr="Module declaration should be in a file named 'module-info.java'">module M</error> { }""")
    myFixture.checkHighlighting()
  }

  fun testFileDuplicate() {
    addFile("pkg/module-info.java", """module M.bis { }""")
    doTest("""<error descr="'module-info.java' already exists in the module">module M</error> { }""")
  }

  fun testWrongFileLocation() {
    val file = addFile("pkg/module-info.java", """<warning descr="Module declaration should be located in a module's source root">module M</warning> { }""")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.checkHighlighting()
  }

  fun testDuplicateStatements() {
    addFile("pkg/main/C.java", "package pkg.main;\npublic class C { }")
    doTest("""
        module M {
          requires M2;
          <error descr="Duplicate requires: M2">requires M2;</error>
          exports pkg.main;
          <error descr="Duplicate export: pkg.main">exports pkg. main;</error>
          uses pkg.main.C;
          <error descr="Duplicate uses: pkg.main.C">uses pkg. main . /*...*/ C;</error>
        }""".trimIndent(), true)
  }

  fun testRequires() {
    doTest("""
        module M1 {
          requires <error descr="Module not found: M.missing">M.missing</error>;
          requires <error descr="Cyclic dependence: M1">M1</error>;
          requires <error descr="Cyclic dependence: M1, M2">M2</error>;
        }""".trimIndent(), true)
  }

  fun testExports() {
    addFile("pkg/empty/package-info.java", "package pkg.empty;")
    addFile("pkg/main/C.java", "package pkg.main;\nclass C { }")
    addFile("pkg/other/C.groovy", "package pkg.other\nclass C { }")
    doTest("""
        module M {
          exports pkg.<error descr="Cannot resolve symbol 'missing'">missing</error>;
          exports <error descr="Package is empty: pkg.empty">pkg.empty</error>;
          exports pkg.main to <error descr="Module not found: M.missing">M.missing</error>, M2, <error descr="Duplicate export: M2">M2</error>;
          exports pkg.other to <warning descr="Exports to itself">M</warning>;
        }""".trimIndent())
  }

  fun testUses() {
    addFile("pkg/main/C.java", "package pkg.main;\nclass C { }")
    addFile("pkg/main/E.java", "package pkg.main;\npublic enum E { }")
    doTest("""
        module M {
          uses pkg.<error descr="Cannot resolve symbol 'main'">main</error>;
          uses pkg.main.<error descr="Cannot resolve symbol 'X'">X</error>;
          uses pkg.main.<error descr="'pkg.main.C' is not public in 'pkg.main'. Cannot be accessed from outside package">C</error>;
          uses pkg.main.<error descr="The service definition is an enum: E">E</error>;
        }""".trimIndent())
  }

  //<editor-fold desc="Helpers.">
  private fun addFile(path: String, text: String) = VfsTestUtil.createFile(LightPlatformTestCase.getSourceRoot(), path, text)

  private fun doTest(text: String, filter: Boolean = false) {
    myFixture.configureByText("module-info.java", text)
    if (filter) {
      (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter { it.name != PsiJavaModule.MODULE_INFO_FILE }
    }
    myFixture.checkHighlighting()
  }
  //</editor-fold>
}