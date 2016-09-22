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
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M3
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.assertj.core.api.Assertions.assertThat

class ModuleHighlightingTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    addFile("module-info.java", "module M2 { }", M2)
    addFile("module-info.java", "module M3 { }", M3)
  }

  fun testWrongFileName() {
    highlight("M.java", """/* ... */ <error descr="Module declaration should be in a file named 'module-info.java'">module M</error> { }""")
  }

  fun testFileDuplicate() {
    addFile("pkg/module-info.java", """module M.bis { }""")
    highlight("""<error descr="'module-info.java' already exists in the module">module M</error> { }""")
  }

  fun testWrongFileLocation() {
    highlight("pkg/module-info.java", """<warning descr="Module declaration should be located in a module's source root">module M</warning> { }""")
  }

  fun testDuplicateStatements() {
    addFile("pkg/main/C.java", "package pkg.main;\npublic class C { }")
    addFile("pkg/main/Impl.java", "package pkg.main;\npublic class Impl extends C { }")
    highlight("""
        module M {
          requires M2;
          <error descr="Duplicate requires: M2">requires M2;</error>
          exports pkg.main;
          <error descr="Duplicate export: pkg.main">exports pkg. main;</error>
          uses pkg.main.C;
          <error descr="Duplicate uses: pkg.main.C">uses pkg. main . /*...*/ C;</error>
          provides pkg .main .C with pkg.main.Impl;
          <error descr="Duplicate provides: pkg.main.C / pkg.main.Impl">provides pkg.main.C with pkg. main. Impl;</error>
        }""".trimIndent(), true)
  }

  fun testUnusedStatements() {
    addFile("pkg/main/C.java", "package pkg.main;\npublic class C { }")
    addFile("pkg/main/Impl.java", "package pkg.main;\npublic class Impl extends C { }")
    highlight("""
        module M {
          provides pkg.main.<warning descr="Service interface provided but not exported or used">C</warning> with pkg.main.Impl;
        }""".trimIndent(), true)
  }

  fun testRequires() {
    addFile("module-info.java", "module M2 { requires M1 }", M2)
    highlight("""
        module M1 {
          requires <error descr="Module not found: M.missing">M.missing</error>;
          requires <error descr="Cyclic dependence: M1">M1</error>;
          requires <error descr="Cyclic dependence: M1, M2">M2</error>;
          requires <error descr="Module is not in dependencies: M3">M3</error>;
        }""".trimIndent(), true)
  }

  fun testExports() {
    addFile("pkg/empty/package-info.java", "package pkg.empty;")
    addFile("pkg/main/C.java", "package pkg.main;\nclass C { }")
    addFile("pkg/other/C.groovy", "package pkg.other\nclass C { }")
    highlight("""
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
    highlight("""
        module M {
          uses pkg.<error descr="Cannot resolve symbol 'main'">main</error>;
          uses pkg.main.<error descr="Cannot resolve symbol 'X'">X</error>;
          uses pkg.main.<error descr="'pkg.main.C' is not public in 'pkg.main'. Cannot be accessed from outside package">C</error>;
          uses pkg.main.<error descr="The service definition is an enum: E">E</error>;
        }""".trimIndent())
  }

  fun testProvides() {
    addFile("pkg/main/C.java", "package pkg.main;\npublic interface C { }")
    addFile("pkg/main/Impl1.java", "package pkg.main;\nclass Impl1 { }")
    addFile("pkg/main/Impl2.java", "package pkg.main;\npublic class Impl2 { }")
    addFile("pkg/main/Impl3.java", "package pkg.main;\npublic abstract class Impl3 implements C { }")
    addFile("pkg/main/Impl4.java", "package pkg.main;\npublic class Impl4 implements C {\n public Impl4(String s) { }\n}")
    addFile("pkg/main/Impl5.java", "package pkg.main;\npublic class Impl5 implements C {\n protected Impl5() { }\n}")
    highlight("""
        module M {
          provides pkg.main.C with pkg.main.<error descr="'pkg.main.Impl1' is not public in 'pkg.main'. Cannot be accessed from outside package">Impl1</error>;
          provides pkg.main.C with pkg.main.<error descr="The service implementation type must be a subtype of the service interface type">Impl2</error>;
          provides pkg.main.C with pkg.main.<error descr="The service implementation is an abstract class: Impl3">Impl3</error>;
          provides pkg.main.C with pkg.main.<error descr="The service implementation does not have a default constructor: Impl4">Impl4</error>;
          provides pkg.main.C with pkg.main.<error descr="The default constructor of the service implementation is not public: Impl5">Impl5</error>;
        }""".trimIndent())
  }

  fun testModuleRefFixes() {
    fixes("module M { requires <caret>M.missing; }")
    fixes("module M { requires <caret>M3; }", "AddModuleDependencyFix")
  }

  fun testPackageRefFixes() {
    fixes("module M { exports <caret>pkg.missing; }")
    addFile("pkg/m3/C3.java", "package pkg.m3;\npublic class C3 { }", M3)
    fixes("module M { exports <caret>pkg.m3; }")
  }

  fun testClassRefFixes() {
    addFile("pkg/m3/C3.java", "package pkg.m3;\npublic class C3 { }", M3)
    fixes("module M { uses <caret>pkg.m3.C3; }", "AddModuleDependencyFix")
  }

  //<editor-fold desc="Helpers.">
  private fun highlight(text: String, filter: Boolean = false) = highlight("module-info.java", text, filter)

  private fun highlight(path: String, text: String, filter: Boolean = false) {
    myFixture.configureFromExistingVirtualFile(addFile(path, text))
    if (filter) {
      (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter { it.name != PsiJavaModule.MODULE_INFO_FILE }
    }
    myFixture.checkHighlighting()
  }

  private fun fixes(text: String, vararg fixes: String) {
    myFixture.configureByText("module-info.java", text)
    assertThat(myFixture.getAllQuickFixes().map { it.javaClass.simpleName }).containsExactlyInAnyOrder(*fixes)
  }
  //</editor-fold>
}