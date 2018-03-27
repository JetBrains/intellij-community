/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInsight.daemon.impl.JavaHighlightInfoTypes
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInspection.deprecation.DeprecationInspection
import com.intellij.codeInspection.deprecation.MarkedForRemovalInspection
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*
import com.intellij.openapi.util.TextRange
import org.assertj.core.api.Assertions.assertThat

class ModuleHighlightingTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    addFile("module-info.java", "module M2 { }", M2)
    addFile("module-info.java", "module M3 { }", M3)
  }

  fun testPackageStatement() {
    highlight("package pkg;")
    highlight("""
        <error descr="A module file should not have 'package' statement">package pkg;</error>
        module M { }""".trimIndent())
    fixes("<caret>package pkg;\nmodule M { }", arrayOf("DeleteElementFix"))
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

  fun testFileDuplicateInTestRoot() {
    addTestFile("module-info.java", """module M.test { }""")
    highlight("""<error descr="'module-info.java' already exists in the module">module M</error> { }""")
  }

  fun testWrongFileLocation() {
    highlight("pkg/module-info.java", """<error descr="Module declaration should be located in a module's source root">module M</error> { }""")
  }

  fun testIncompatibleModifiers() {
    highlight("""
        module M {
          requires static transitive M2;
          requires <error descr="Modifier 'private' not allowed here">private</error> <error descr="Modifier 'volatile' not allowed here">volatile</error> M3;
        }""".trimIndent())
  }

  fun testAnnotations() {
    highlight("""@Deprecated <error descr="'@Override' not applicable to module">@Override</error> module M { }""")
  }

  fun testDuplicateStatements() {
    addFile("pkg/main/C.java", "package pkg.main;\npublic class C { }")
    addFile("pkg/main/Impl.java", "package pkg.main;\npublic class Impl extends C { }")
    highlight("""
        import pkg.main.C;
        module M {
          requires M2;
          <error descr="Duplicate 'requires': M2">requires M2;</error>
          exports pkg.main;
          <error descr="Duplicate 'exports': pkg.main">exports pkg. main;</error>
          opens pkg.main;
          <error descr="Duplicate 'opens': pkg.main">opens pkg. main;</error>
          uses C;
          <error descr="Duplicate 'uses': pkg.main.C">uses pkg. main . /*...*/ C;</error>
          provides pkg .main .C with pkg.main.Impl;
          <error descr="Duplicate 'provides': pkg.main.C">provides C with pkg. main. Impl;</error>
        }""".trimIndent())
  }

  fun testUnusedServices() {
    addFile("pkg/main/C1.java", "package pkg.main;\npublic class C1 { }")
    addFile("pkg/main/C2.java", "package pkg.main;\npublic class C2 { }")
    addFile("pkg/main/C3.java", "package pkg.main;\npublic class C3 { }")
    addFile("pkg/main/Impl1.java", "package pkg.main;\npublic class Impl1 extends C1 { }")
    addFile("pkg/main/Impl2.java", "package pkg.main;\npublic class Impl2 extends C2 { }")
    addFile("pkg/main/Impl3.java", "package pkg.main;\npublic class Impl3 extends C3 { }")
    highlight("""
        import pkg.main.C2;
        import pkg.main.C3;
        module M {
          uses C2;
          uses pkg.main.C3;
          provides pkg.main.<warning descr="Service interface provided but not exported or used">C1</warning> with pkg.main.Impl1;
          provides pkg.main.C2 with pkg.main.Impl2;
          provides C3 with pkg.main.Impl3;
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
          requires lib.named;
          requires lib.claimed;
        }""".trimIndent())
  }

  fun testExports() {
    addFile("pkg/empty/package-info.java", "package pkg.empty;")
    addFile("pkg/main/C.java", "package pkg.main;\nclass C { }")
    addFile("pkg/other/C.groovy", "package pkg.other\nclass C { }")
    highlight("""
        module M {
          exports <error descr="Package not found: pkg.missing.unknown">pkg.missing.unknown</error>;
          exports <error descr="Package is empty: pkg.empty">pkg.empty</error>;
          exports pkg.main to <warning descr="Module not found: M.missing">M.missing</warning>, M2, <error descr="Duplicate 'exports' target: M2">M2</error>;
        }""".trimIndent())
  }

  fun testOpens() {
    addFile("pkg/empty/package-info.java", "package pkg.empty;")
    addFile("pkg/main/C.java", "package pkg.main;\nclass C { }")
    addFile("pkg/other/C.groovy", "package pkg.other\nclass C { }")
    highlight("""
        module M {
          opens <warning descr="Package not found: pkg.missing.unknown">pkg.missing.unknown</warning>;
          opens <warning descr="Package is empty: pkg.empty">pkg.empty</warning>;
          opens pkg.main to <warning descr="Module not found: M.missing">M.missing</warning>, M2, <error descr="Duplicate 'opens' target: M2">M2</error>;
        }""".trimIndent())
  }

  fun testWeakModule() {
    highlight("""open module M { <error descr="'opens' is not allowed in an open module">opens pkg.missing;</error> }""")
  }

  fun testUses() {
    addFile("pkg/main/C.java", "package pkg.main;\nclass C { void m(); }")
    addFile("pkg/main/O.java", "package pkg.main;\npublic class O {\n public class I { void m(); }\n}")
    addFile("pkg/main/E.java", "package pkg.main;\npublic enum E { }")
    highlight("""
        module M {
          uses pkg.<error descr="Cannot resolve symbol 'main'">main</error>;
          uses pkg.main.<error descr="Cannot resolve symbol 'X'">X</error>;
          uses pkg.main.<error descr="'pkg.main.C' is not public in 'pkg.main'. Cannot be accessed from outside package">C</error>;
          uses pkg.main.<error descr="The service definition is an enum: E">E</error>;
          uses pkg.main.O.I;
          uses pkg.main.O.I.<error descr="Cannot resolve symbol 'm'">m</error>;
        }""".trimIndent())
  }

  fun testProvides() {
    addFile("pkg/main/C.java", "package pkg.main;\npublic interface C { }")
    addFile("pkg/main/CT.java", "package pkg.main;\npublic interface CT<T> { }")
    addFile("pkg/main/Impl1.java", "package pkg.main;\nclass Impl1 { }")
    addFile("pkg/main/Impl2.java", "package pkg.main;\npublic class Impl2 { }")
    addFile("pkg/main/Impl3.java", "package pkg.main;\npublic abstract class Impl3 implements C { }")
    addFile("pkg/main/Impl4.java", "package pkg.main;\npublic class Impl4 implements C {\n public Impl4(String s) { }\n}")
    addFile("pkg/main/Impl5.java", "package pkg.main;\npublic class Impl5 implements C {\n protected Impl5() { }\n}")
    addFile("pkg/main/Impl6.java", "package pkg.main;\npublic class Impl6 implements C { }")
    addFile("pkg/main/Impl7.java", "package pkg.main;\npublic class Impl7 {\n public static void provider();\n}")
    addFile("pkg/main/Impl8.java", "package pkg.main;\npublic class Impl8 {\n public static C provider();\n}")
    addFile("pkg/main/Impl9.java", "package pkg.main;\npublic class Impl9 {\n public class Inner implements C { }\n}")
    addFile("pkg/main/Impl10.java", "package pkg.main;\npublic class Impl10<T> implements CT<T> {\n private Impl10() { }\n public static CT provider();\n}")
    addFile("module-info.java", "module M2 {\n exports pkg.m2;\n}", M2)
    addFile("pkg/m2/C.java", "package pkg.m2;\npublic class C { }", M2)
    addFile("pkg/m2/Impl.java", "package pkg.m2;\npublic class Impl extends C { }", M2)
    highlight("""
        import pkg.main.Impl6;
        module M {
          requires M2;
          provides pkg.main.C with pkg.main.<error descr="Cannot resolve symbol 'NoImpl'">NoImpl</error>;
          provides pkg.main.C with pkg.main.<error descr="'pkg.main.Impl1' is not public in 'pkg.main'. Cannot be accessed from outside package">Impl1</error>;
          provides pkg.main.C with pkg.main.<error descr="The service implementation type must be a subtype of the service interface type, or have a public static no-args 'provider' method">Impl2</error>;
          provides pkg.main.C with pkg.main.<error descr="The service implementation is an abstract class: Impl3">Impl3</error>;
          provides pkg.main.C with pkg.main.<error descr="The service implementation does not have a public default constructor: Impl4">Impl4</error>;
          provides pkg.main.C with pkg.main.<error descr="The service implementation does not have a public default constructor: Impl5">Impl5</error>;
          provides pkg.main.C with pkg.main.Impl6, <error descr="Duplicate implementation: pkg.main.Impl6">Impl6</error>;
          provides pkg.main.C with pkg.main.<error descr="The 'provider' method return type must be a subtype of the service interface type: Impl7">Impl7</error>;
          provides pkg.main.C with pkg.main.Impl8;
          provides pkg.main.C with pkg.main.Impl9.<error descr="The service implementation is an inner class: Inner">Inner</error>;
          provides pkg.main.CT with pkg.main.Impl10;
          provides pkg.m2.C with pkg.m2.<error descr="The service implementation must be defined in the same module as the provides directive">Impl</error>;
        }""".trimIndent())
  }

  fun testQuickFixes() {
    addFile("pkg/main/impl/X.java", "package pkg.main.impl;\npublic class X { }")
    addFile("module-info.java", "module M2 { exports pkg.m2; }", M2)
    addFile("pkg/m2/C2.java", "package pkg.m2;\npublic class C2 { }", M2)
    addFile("pkg/m3/C3.java", "package pkg.m3;\npublic class C3 { }", M3)
    addFile("module-info.java", "module M6 { exports pkg.m6; }", M6)
    addFile("pkg/m6/C6.java", "package pkg.m6;\nimport pkg.m8.*;\nimport java.util.function.*;\npublic class C6 { public void m(Consumer<C8> c) { } }", M6)
    addFile("module-info.java", "module M8 { exports pkg.m8; }", M8)
    addFile("pkg/m8/C8.java", "package pkg.m8;\npublic class C8 { }", M8)

    fixes("module M { requires <caret>M.missing; }", arrayOf())
    fixes("module M { requires <caret>M3; }", arrayOf("AddModuleDependencyFix"))
    fixes("module M { exports pkg.main.impl to <caret>M3; }", arrayOf())
    fixes("module M { exports <caret>pkg.missing; }", arrayOf("CreateClassInPackageInModuleFix"))
    fixes("module M { exports <caret>pkg.m3; }", arrayOf())
    fixes("module M { uses pkg.m3.<caret>C3; }", arrayOf("AddModuleDependencyFix"))
    fixes("pkg/main/C.java", "package pkg.main;\nimport <caret>pkg.m2.C2;", arrayOf("AddRequiresDirectiveFix"))

    addFile("module-info.java", "module M { requires M6; }")
    addFile("pkg/main/Util.java", "package pkg.main;\nclass Util {\n static <T> void sink(T t) { }\n}")
    fixes("pkg/main/C.java", "package pkg.main;\nimport pkg.m6.*;class C {{ new C6().m(<caret>Util::sink); }}", arrayOf("AddRequiresDirectiveFix"))
    fixes("pkg/main/C.java", "package pkg.main;\nimport pkg.m6.*;class C {{ new C6().m(<caret>t -> Util.sink(t)); }}", arrayOf("AddRequiresDirectiveFix"))

    addFile("module-info.java", "module M2 { }", M2)
    fixes("module M { requires M2; uses <caret>pkg.m2.C2; }", arrayOf("AddExportsDirectiveFix"))
    fixes("pkg/main/C.java", "package pkg.main;\nimport <caret>pkg.m2.C2;", arrayOf("AddExportsDirectiveFix"))

    addFile("pkg/main/S.java", "package pkg.main;\npublic class S { }")
    fixes("module M { provides pkg.main.<caret>S with pkg.main.S; }", arrayOf("AddExportsDirectiveFix", "AddUsesDirectiveFix"))
  }

  fun testPackageAccessibility() = doTestPackageAccessibility(moduleFileInTests = false, checkFileInTests = false)
  fun testPackageAccessibilityInNonModularTest() = doTestPackageAccessibility(moduleFileInTests = true, checkFileInTests = false)
  fun testPackageAccessibilityInModularTest() = doTestPackageAccessibility(moduleFileInTests = true, checkFileInTests = true)

  private fun doTestPackageAccessibility(moduleFileInTests: Boolean = false, checkFileInTests: Boolean = false) {
    val moduleFileText = "module M { requires M2; requires M6; requires lib.named; requires lib.auto; }"
    if (moduleFileInTests) addTestFile("module-info.java", moduleFileText) else addFile("module-info.java", moduleFileText)

    addFile("module-info.java", "module M2 { exports pkg.m2; exports pkg.m2.impl to close.friends.only; }", M2)
    addFile("pkg/m2/C2.java", "package pkg.m2;\npublic class C2 { }", M2)
    addFile("pkg/m2/impl/C2Impl.java", "package pkg.m2.impl;\nimport pkg.m2.C2;\npublic class C2Impl { public static int I; public static C2 make() {} }", M2)
    addFile("pkg/sub/C2X.java", "package pkg.sub;\npublic class C2X { }", M2)
    addFile("pkg/unreachable/C3.java", "package pkg.unreachable;\npublic class C3 { }", M3)
    addFile("pkg/m4/C4.java", "package pkg.m4;\npublic class C4 { }", M4)
    addFile("module-info.java", "module M5 { exports pkg.m5; }", M5)
    addFile("pkg/m5/C5.java", "package pkg.m5;\npublic class C5 { }", M5)
    addFile("module-info.java", "module M6 { requires transitive M7; }", M6)
    addFile("pkg/sub/C6X.java", "package pkg.sub;\npublic class C6X { }", M6)
    addFile("module-info.java", "module M7 { exports pkg.m7; }", M7)
    addFile("pkg/m7/C7.java", "package pkg.m7;\npublic class C7 { }", M7)

    var checkFileText = """
        import pkg.m2.C2;
        import pkg.m2.*;
        import <error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">pkg.m2.impl</error>.C2Impl;
        import <error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">pkg.m2.impl</error>.*;
        import <error descr="Package 'pkg.m4' is declared in the unnamed module, but module 'M' does not read it">pkg.m4</error>.C4;
        import <error descr="Package 'pkg.m5' is declared in module 'M5', but module 'M' does not read it">pkg.m5</error>.C5;
        import pkg.m7.C7;
        import <error descr="Package 'pkg.sub' is declared in module 'M2', which does not export it to module 'M'">pkg.sub</error>.*;
        import <error descr="Package not found: pkg.unreachable">pkg.unreachable</error>.*;

        import pkg.lib1.LC1;
        import <error descr="Package 'pkg.lib1.impl' is declared in module 'lib.named', which does not export it to module 'M'">pkg.lib1.impl</error>.LC1Impl;
        import <error descr="Package 'pkg.lib1.impl' is declared in module 'lib.named', which does not export it to module 'M'">pkg.lib1.impl</error>.*;

        import pkg.lib2.LC2;
        import pkg.lib2.impl.LC2Impl;

        import static <error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">pkg.m2.impl</error>.C2Impl.make;

        import java.util.List;
        import java.util.function.Supplier;

        import <error descr="Package 'pkg.libInvalid' is declared in module with an invalid name ('lib.invalid.1.2')">pkg.libInvalid</error>.LCInv;

        /** See also {@link C2Impl#I} and {@link C2Impl#make} */
        class C {{
          <error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">C2Impl</error>.I = 0;
          <error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">C2Impl</error>.make();
          <error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">pkg.m2.impl</error>.C2Impl.I = 1;
          <error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">pkg.m2.impl</error>.C2Impl.make();

          List<<error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">C2Impl</error>> l1 = null;
          List<<error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">pkg.m2.impl</error>.C2Impl> l2 = null;

          Supplier<C2> s1 = <error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">C2Impl</error>::make;
          Supplier<C2> s2 = <error descr="Package 'pkg.m2.impl' is declared in module 'M2', which does not export it to module 'M'">pkg.m2.impl</error>.C2Impl::make;
        }}
        """.trimIndent()
    if (moduleFileInTests != checkFileInTests) {
      checkFileText = Regex("(<error [^>]+>)([^<]+)(</error>)").replace(checkFileText, {
        if (it.value.contains("unreachable")) it.value else it.groups[2]!!.value
      })
    }
    highlight("test.java", checkFileText, checkFileInTests)
  }

  fun testLinearModuleGraphBug() {
    addFile("module-info.java", "module M6 { requires M7; }", M6)
    addFile("module-info.java", "module M7 { }", M7)
    highlight("module M { requires M6; }")
  }

  fun testDeprecations() {
    myFixture.enableInspections(DeprecationInspection(), MarkedForRemovalInspection())
    addFile("module-info.java", "@Deprecated module M2 { }", M2)
    highlight("""module M { requires <warning descr="'M2' is deprecated">M2</warning>; }""")
  }

  fun testMarkedForRemoval() {
    myFixture.enableInspections(DeprecationInspection(), MarkedForRemovalInspection())
    addFile("module-info.java", "@Deprecated(forRemoval=true) module M2 { }", M2)
    highlight("""module M { requires <error descr="'M2' is deprecated and marked for removal">M2</error>; }""")
  }

  fun testPackageConflicts() {
    addFile("pkg/collision2/C2.java", "package pkg.collision2;\npublic class C2 { }", M2)
    addFile("pkg/collision4/C4.java", "package pkg.collision4;\npublic class C4 { }", M4)
    addFile("pkg/collision7/C7.java", "package pkg.collision7;\npublic class C7 { }", M7)
    addFile("module-info.java", "module M2 { exports pkg.collision2; }", M2)
    addFile("module-info.java", "module M4 { exports pkg.collision4 to M88; }", M4)
    addFile("module-info.java", "module M6 { requires transitive M7; }", M6)
    addFile("module-info.java", "module M7 { exports pkg.collision7 to M6; }", M7)
    addFile("module-info.java", "module M { requires M2; requires M4; requires M6; requires lib.auto; }")
    highlight("test1.java", """<error descr="Package 'pkg.collision2' exists in another module: M2">package pkg.collision2;</error>""")
    highlight("test2.java", """package pkg.collision4;""")
    highlight("test3.java", """package pkg.collision7;""")
    highlight("test4.java", """<error descr="Package 'java.util' exists in another module: java.base">package java.util;</error>""")
    highlight("test5.java", """<error descr="Package 'pkg.lib2' exists in another module: lib.auto">package pkg.lib2;</error>""")
  }

  fun testClashingReads1() {
    addFile("pkg/collision/C2.java", "package pkg.collision;\npublic class C2 { }", M2)
    addFile("pkg/collision/C7.java", "package pkg.collision;\npublic class C7 { }", M7)
    addFile("module-info.java", "module M2 { exports pkg.collision; }", M2)
    addFile("module-info.java", "module M6 { requires transitive M7; }", M6)
    addFile("module-info.java", "module M7 { exports pkg.collision; }", M7)
    highlight("""
        <error descr="Module 'M' reads package 'pkg.collision' from both 'M2' and 'M7'">module M</error> {
          requires M2;
          requires M6;
        }""".trimIndent())
  }

  fun testClashingReads2() {
    addFile("pkg/collision/C2.java", "package pkg.collision;\npublic class C2 { }", M2)
    addFile("pkg/collision/C4.java", "package pkg.collision;\npublic class C4 { }", M4)
    addFile("module-info.java", "module M2 { exports pkg.collision; }", M2)
    addFile("module-info.java", "module M4 { exports pkg.collision to somewhere; }", M4)
    highlight("module M { requires M2; requires M4; }")
  }

  fun testClashingReads3() {
    addFile("pkg/lib2/C2.java", "package pkg.lib2;\npublic class C2 { }", M2)
    addFile("module-info.java", "module M2 { exports pkg.lib2; }", M2)
    highlight("""
        <error descr="Module 'M' reads package 'pkg.lib2' from both 'M2' and 'lib.auto'">module M</error> {
          requires M2;
          requires <warning descr="Ambiguous module reference: lib.auto">lib.auto</warning>;
        }""".trimIndent())
  }

  fun testInaccessibleMemberType() {
    addFile("module-info.java", "module C { exports pkg.c; }", M8)
    addFile("module-info.java", "module B { requires C; exports pkg.b; }", M6)
    addFile("module-info.java", "module A { requires B; }")
    addFile("pkg/c/C.java", "package pkg.c;\npublic class C { }", M8)
    addFile("pkg/b/B.java", """
        package pkg.b;
        import pkg.c.C;
        public class B {
          private static class I { }
          public C f1;
          public I f2;
          public C m1(C p1, Class<? extends C> p2) { return new C(); }
          public I m2(I p1, Class<? extends I> p2) { return new I(); }
        }""".trimIndent(), M6)
    highlight("pkg/a/A.java", """
        package pkg.a;
        import pkg.b.B;
        public class A {
          void test() {
            B exposer = new B();
            exposer.f1 = null;
            exposer.f2 = null;
            Object o1 = exposer.m1(null, null);
            Object o2 = exposer.m2(null, null);
          }
        }""".trimIndent())
  }

  fun testAccessingDefaultPackage() {
    addFile("X.java", "public class X {\n  public static class XX extends X { }\n}")
    highlight("""
        module M {
          uses <error descr="Class 'X' is in the default package">X</error>;
          provides <error descr="Class 'X' is in the default package">X</error> with <error descr="Class 'X' is in the default package">X</error>.XX;
        }""".trimIndent())
  }

  //<editor-fold desc="Helpers.">
  private fun highlight(text: String) = highlight("module-info.java", text)

  private fun highlight(path: String, text: String, isTest: Boolean = false) {
    myFixture.configureFromExistingVirtualFile(if (isTest) addTestFile(path, text) else addFile(path, text))
    myFixture.checkHighlighting()
  }

  private fun fixes(text: String, fixes: Array<String>) = fixes("module-info.java", text, fixes)

  private fun fixes(path: String, text: String, fixes: Array<String>) {
    myFixture.configureFromExistingVirtualFile(addFile(path, text))
    val available = myFixture.availableIntentions
      .map { (if (it is IntentionActionDelegate) it.delegate else it)::class.simpleName }
      .filter { it != "GutterIntentionAction" }
    assertThat(available).containsExactlyInAnyOrder(*fixes)
  }
  //</editor-fold>
}