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

import com.intellij.codeInsight.daemon.impl.JavaHighlightInfoTypes
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*
import org.assertj.core.api.Assertions.assertThat

class ModuleHighlightingTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    addFile("module-info.java", "module M2 { }", M2)
    addFile("module-info.java", "module M3 { }", M3)
  }

  fun testSoftKeywords() {
    addFile("pkg/module/C.java", "package pkg.module;\npublic class C { }")
    myFixture.configureByText("module-info.java", "module M { exports pkg.module; }")
    val keywords = myFixture.doHighlighting().filter { it.type == JavaHighlightInfoTypes.JAVA_KEYWORD }
    assertEquals(2, keywords.size)
    assertEquals(TextRange(0, 6), TextRange(keywords[0].startOffset, keywords[0].endOffset))
    assertEquals(TextRange(11, 18), TextRange(keywords[1].startOffset, keywords[1].endOffset))
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
        }""".trimIndent())
  }

  fun testUnusedStatements() {
    addFile("pkg/main/C.java", "package pkg.main;\npublic class C { }")
    addFile("pkg/main/Impl.java", "package pkg.main;\npublic class Impl extends C { }")
    highlight("""
        module M {
          provides pkg.main.<warning descr="Service interface provided but not exported or used">C</warning> with pkg.main.Impl;
        }""".trimIndent())
  }

  fun testRequires() {
    addFile("module-info.java", "module M2 { requires M1 }", M2)
    highlight("""
        module M1 {
          requires <error descr="Module not found: M.missing">M.missing</error>;
          requires <error descr="Cyclic dependence: M1">M1</error>;
          requires <error descr="Cyclic dependence: M1, M2">M2</error>;
          requires <error descr="Module is not in dependencies: M3">M3</error>;
          requires <warning descr="Ambiguous module reference: lib.auto">lib.auto</warning>;
          requires lib.multi.release;
        }""".trimIndent())
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

  fun testPackageAccessibility() {
    addFile("module-info.java", "module M { requires M2; requires M6; requires lib.named; requires lib.auto; }")
    addFile("module-info.java", "module M2 { exports pkg.m2; exports pkg.m2.impl to close.friends.only; }", M2)
    addFile("pkg/m2/C2.java", "package pkg.m2;\npublic class C2 { }", M2)
    addFile("pkg/m2/impl/C2Impl.java", "package pkg.m2.impl;\nimport pkg.m2.C2;\npublic class C2Impl { public static C2 make() {} }", M2)
    addFile("pkg/m4/C4.java", "package pkg.m4;\npublic class C4 { }", M4)
    addFile("module-info.java", "module M5 { exports pkg.m5; }", M5)
    addFile("pkg/m5/C5.java", "package pkg.m5;\npublic class C5 { }", M5)
    addFile("module-info.java", "module M6 { requires public M7; }", M6)
    addFile("module-info.java", "module M7 { exports pkg.m7; }", M7)
    addFile("pkg/m7/C7.java", "package pkg.m7;\npublic class C7 { }", M7)

    highlight("test.java", """
        import pkg.m2.C2;
        import pkg.m2.*;
        import <error descr="The module 'M2' does not export the package 'pkg.m2.impl' to the module 'M'">pkg.m2.impl.C2Impl</error>;
        import <error descr="The module 'M2' does not export the package 'pkg.m2.impl' to the module 'M'">pkg.m2.impl</error>.*;
        import <error descr="A named module cannot access packages of an unnamed one">pkg.m4.C4</error>;
        import <error descr="The module 'M' does not have the module 'M5' in requirements">pkg.m5.C5</error>;
        import pkg.m7.C7;

        import pkg.lib1.LC1;
        import <error descr="The module 'lib.named' does not export the package 'pkg.lib1.impl' to the module 'M'">pkg.lib1.impl.LC1Impl</error>;
        import <error descr="The module 'lib.named' does not export the package 'pkg.lib1.impl' to the module 'M'">pkg.lib1.impl</error>.*;

        import pkg.lib2.LC2;
        import pkg.lib2.impl.LC2Impl;

        import static <error descr="The module 'M2' does not export the package 'pkg.m2.impl' to the module 'M'">pkg.m2.impl.C2Impl</error>.make;

        class C { }
        """.trimIndent())
  }

  fun testLinearModuleGraphBug() {
    addFile("module-info.java", "module M { requires M6; }")
    addFile("module-info.java", "module M6 { requires M7; }", M6)
    addFile("module-info.java", "module M7 { }", M7)
    highlight("module M { requires M6; }")
  }

  //<editor-fold desc="Helpers.">
  private fun highlight(text: String) = highlight("module-info.java", text)

  private fun highlight(path: String, text: String) {
    myFixture.configureFromExistingVirtualFile(addFile(path, text))
    myFixture.checkHighlighting()
  }

  private fun fixes(text: String, vararg fixes: String) {
    myFixture.configureByText("module-info.java", text)
    assertThat(myFixture.getAllQuickFixes().map { it.javaClass.simpleName }).containsExactlyInAnyOrder(*fixes)
  }
  //</editor-fold>
}