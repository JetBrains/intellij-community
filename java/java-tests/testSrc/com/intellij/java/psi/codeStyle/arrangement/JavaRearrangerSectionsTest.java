// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PRIVATE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC;

public class JavaRearrangerSectionsTest extends AbstractJavaRearrangerTest {
  public void test_single_section() {
    doTest("""

             class MyClass
             {
               // --- Public Methods ---

               public void test() {}

               // --- End Public Methods ---

               private int number;
             }""", """

             class MyClass
             {
               private int number;

               // --- Public Methods ---
               public void test() {}
               // --- End Public Methods ---
             }""", List.of(rule(FIELD),
                           section("// --- Public Methods ---",
                                   "// --- End Public Methods ---",
                                   rule(METHOD))));
  }

  public void test_multi_section() {
    doTest("""

             class MyClass
             {
               // --- Private Methods ---

               private void foo() {}

               // --- End Private Methods ---

               // --- Public Methods ---

               public void test() {}

               // --- End Public Methods ---

               public int p;
             }""", """

             class MyClass
             {
               public int p;

               // --- Public Methods ---
               public void test() {}
               // --- End Public Methods ---

               // --- Private Methods ---
               private void foo() {}
               // --- End Private Methods ---
             }""", List.of(section(rule(FIELD)),
                           section("// --- Public Methods ---",
                                   "// --- End Public Methods ---",
                                   rule(PUBLIC, METHOD)),
                           section("// --- Private Methods ---",
                                   "// --- End Private Methods ---",
                                   rule(PRIVATE, METHOD))));
  }

  public void test_multi_region() {
    doTest("""

             class MyClass
             {
               //region Private Methods

               private void foo() {}

               //endregion private

               //region Public Methods

               public void test() {}

               //endregion public

               public int p;
             }""", """

             class MyClass
             {
               public int p;

               //region Public Methods
               public void test() {}
               //endregion public

               //region Private Methods
               private void foo() {}
               //endregion private
             }""",
           List.of(
             section(rule(FIELD)),
             section("//region Public Methods", "//endregion public", rule(PUBLIC, METHOD)),
             section("//region Private Methods", "//endregion private", rule(PRIVATE, METHOD))));
  }

  public void test_new_single_section() {
    doTest("""

             class MyClass
             {
               public void test() {}

               public int p;
             }""", """

             class MyClass
             {
               public int p;

             // --- Public Methods ---
               public void test() {}
             // --- End Public Methods ---
             }""",
           List.of(
             section(rule(FIELD)),
             section("// --- Public Methods ---", "// --- End Public Methods ---", rule(METHOD))));
  }

  public void test_all_new_multi_section_without_comments() {
    doTest("""

             class MyClass
             {
               public void test() {}

               public int p;
             }""", """

             class MyClass
             {
               public int p;

             // --- Public Methods ---
               public void test() {}
             // --- End Public Methods ---
             }""", List.of(section(rule(FIELD)),
                           section("// --- Public Methods ---",
                                   "// --- End Public Methods ---",
                                   rule(METHOD))));
  }

  public void test_new_multi_section() {
    doTest("""

             class MyClass
             {
               public int p;

               public void test() {}
             }""", """

             class MyClass
             {
             // --- Fields ---
               public int p;
             // --- Fields ---

             // --- Public Methods ---
               public void test() {}
             // --- End Public Methods ---
             }""", List.of(section("// --- Fields ---", "// --- Fields ---", rule(FIELD)),
                           section("// --- Public Methods ---", "// --- End Public Methods ---",
                                   rule(METHOD))));
  }

  public void test_new_not_arranged_multi_section() {
    doTest("""

             class MyClass
             {
               public void test() {}

               public int p;
             }""", """

             class MyClass
             {
             // --- Fields ---
               public int p;
             // --- Fields ---

             // --- Public Methods ---
               public void test() {}
             // --- End Public Methods ---
             }""", List.of(section("// --- Fields ---", "// --- Fields ---", rule(FIELD)),
                           section("// --- Public Methods ---", "// --- End Public Methods ---",
                                   rule(METHOD))));
  }

  public void test_blank_lines() {
    doTest("""

             class MyClass
             {
               // --- Public Methods ---
               public void test() {}
               // --- End Public Methods ---

               public int p;
             }""", """

             class MyClass
             {
               public int p;

               // --- Public Methods ---
               public void test() {}
               // --- End Public Methods ---
             }""", List.of(rule(FIELD),
                           section("// --- Public Methods ---",
                                   "// --- End Public Methods ---",
                                   rule(METHOD))));
  }

  public void test_multi_rules_in_section() {
    doTest("""

             class MyClass
             {
               private void foo() {
                 System.out.println("Hello!");
               }

               // --- Methods ---
               public void test() {}
               // --- End Methods ---

               public int p;
             }""", """

             class MyClass
             {
               public int p;

               // --- Methods ---
               public void test() {}

               private void foo() {
                 System.out.println("Hello!");
               }
               // --- End Methods ---
             }""", List.of(rule(FIELD),
                           section("// --- Methods ---", "// --- End Methods ---",
                                   rule(PUBLIC, METHOD),
                                   rule(PRIVATE, METHOD))));
  }

  public void test_multi_rules_in_new_section() {
    doTest("""

             class MyClass
             {
               private int i;

               private void foo() {
                 System.out.println("Hello!");
               }

               // --- Methods ---
               public void test() {}
               // --- End Methods ---

               public int p;
             }""", """

             class MyClass
             {
             // --- Properties ---
               public int p;
               private int i;
             // --- End Properties ---

               // --- Methods ---
               public void test() {}

               private void foo() {
                 System.out.println("Hello!");
               }
               // --- End Methods ---
             }""", List.of(
      section("// --- Properties ---", "// --- End Properties ---", rule(PUBLIC, FIELD),
              rule(PRIVATE, FIELD)),
      section("// --- Methods ---", "// --- End Methods ---", rule(PUBLIC, METHOD),
              rule(PRIVATE, METHOD))));
  }

  public void test_section_without_close_comment() {
    doTest("""

             class MyClass
             {
               private void foo() {
                 System.out.println("Hello!");
               }

               // --- Methods ---
               public void test() {}

               public int p;
             }""", """

             class MyClass
             {
               public int p;

               // --- Methods ---
               public void test() {}

               private void foo() {
                 System.out.println("Hello!");
               }
             }""", List.of(section(rule(FIELD)),
                           section("// --- Methods ---", null,
                                   rule(PUBLIC,
                                        METHOD),
                                   rule(PRIVATE,
                                        METHOD))));
  }

  public void test_new_section_without_close_comment() {
    doTest("""

             class MyClass
             {
               private void foo() {
                 System.out.println("Hello!");
               }

               // --- Methods ---
               public void test() {}

               public int p;
             }""", """

             class MyClass
             {
             // --- Properties ---
               public int p;

               // --- Methods ---
               public void test() {}

               private void foo() {
                 System.out.println("Hello!");
               }
             }""", List.of(section("// --- Properties ---", null, rule(FIELD)),
                           section("// --- Methods ---", null, rule(PUBLIC, METHOD),
                                   rule(PRIVATE, METHOD))));
  }

  public void test_new_section_in_hierarchy() {
    doTest("""

             class MyClass
             {
               private void foo() {
                 System.out.println("Hello!");
               }
               public void test() {}
             }""", """

             //region --- Class ---
             class MyClass
             {
             //region --- Methods ---
               public void test() {}

               private void foo() {
                 System.out.println("Hello!");
               }
             //endregion methods
             }
             //endregion class""",
           List.of(section("//region --- Class ---", "//endregion class", rule(CLASS)),
                   section("//region --- Methods ---", "//endregion methods",
                           rule(PUBLIC, METHOD),
                           rule(PRIVATE, METHOD))));
  }

  public void test_new_sections_in_hierarchy() {
    doTest("""

             interface MyI {
             }

             class MyClass
             {
               private void foo() {
                 System.out.println("Hello!");
               }
               public void test() {}
             }""", """

             //region --- Class ---
             class MyClass
             {
             //region --- Methods ---
               public void test() {}

               private void foo() {
                 System.out.println("Hello!");
               }
             //endregion methods
             }

             interface MyI {
             }
             //endregion class""", List.of(
      section("//region --- Class ---", "//endregion class", rule(CLASS),
              rule(INTERFACE)),
      section("//region --- Methods ---", "//endregion methods", rule(PUBLIC, METHOD),
              rule(PRIVATE, METHOD))));
  }

  public void test_two_methods_section_comments_in_a_row() {
    doTest("""

             public class SuperTest {
                 //publicS
                 public void test() {
                 }
             //publicE
             //privateS
                 private void testPrivate() {
                 }
             //privateE

                 public int t;
             }
             """, """

             public class SuperTest {
                 public int t;

                 //publicS
                 public void test() {
                 }
             //publicE

             //privateS
                 private void testPrivate() {
                 }
             //privateE
             }
             """, List.of(rule(FIELD),
                          section("//publicS", "//publicE",
                                  rule(PUBLIC, METHOD)),
                          section("//privateS", "//privateE",
                                  rule(PRIVATE, METHOD))));
  }

  public void test_two_fields_section_comments_in_a_row() {
    doTest("""

             public class SuperTest {
                 public void test() {}

                 //publicFieldStart
                 public int test = 2;
             //publicFieldEnd
             //privateFieldStart
                 private int pr = 1;
             //privateFieldEnd
             }
             """, """

             public class SuperTest {
                 //publicFieldStart
                 public int test = 2;
             //publicFieldEnd
             //privateFieldStart
                 private int pr = 1;
             //privateFieldEnd

                 public void test() {}
             }
             """,
           List.of(section("//publicFieldStart", "//publicFieldEnd", rule(PUBLIC, FIELD)),
                   section("//privateFieldStart", "//privateFieldEnd", rule(PRIVATE, FIELD)),
                   rule(METHOD)));
  }

  public void test_no_additional_sections_added() {
    doTest("""

             //sectionClassStart
             public class Test {
             }
             //sectionClassEnd
             """, """

             //sectionClassStart
             public class Test {
             }
             //sectionClassEnd
             """, List.of(section("//sectionClassStart", "//sectionClassEnd", rule(CLASS))));
  }

  public void test_inner_classes_sections() {
    doTest("""

             //sectionClassStart
             public class Test {

                 interface Testable {
                 }

             }
             //sectionClassEnd
             class Double {
             }
             """, """

             //sectionClassStart
             public class Test {

             //sectionInterfaceStart
                 interface Testable {
                 }
             //sectionInterfaceEnd

             }

             class Double {
             }
             //sectionClassEnd
             """, List.of(section("//sectionClassStart", "//sectionClassEnd", rule(CLASS)),
                          section("//sectionInterfaceStart", "//sectionInterfaceEnd",
                                  rule(INTERFACE))));
  }

  public void test_a_lot_of_sections() {
    List<ArrangementSectionRule> rules = List.of(
      section("//sectionInterfaceStart", "//sectionInterfaceEnd", rule(INTERFACE)),
      section("//sectionEnumStart", "//sectionEnumEnd", rule(ENUM)),
      section("//sectionClassStart", "//sectionClassEnd", rule(CLASS)),
      section("//sectionPublicFieldStart", "//sectionPublicFieldEnd", rule(FIELD, PUBLIC)),
      section("//sectionPrivateFieldStart", "//sectionPrivateFieldEnd", rule(FIELD, PRIVATE)),
      section("//sectionPublicMethodStart", "//sectionPublicMethodEnd", rule(METHOD, PUBLIC)),
      section("//sectionPrivateMethodStart", "//sectionPrivateMethodEnd",
              rule(METHOD, PRIVATE)));

    doTest("""

             public class SuperTest {

                 class T {}

                 class R {}

                 interface I {}

                 enum Q {}

                 public void test() {}

                 private void teste2() {}

                 public void testtt() {}

                 public int a;

                 private int b;
             }

             class Test {}
             """, """

             //sectionClassStart
             public class SuperTest {

             //sectionInterfaceStart
                 interface I {}
             //sectionInterfaceEnd

             //sectionEnumStart
                 enum Q {}
             //sectionEnumEnd

             //sectionClassStart
                 class T {}

                 class R {}
             //sectionClassEnd
             //sectionPublicFieldStart
                 public int a;
             //sectionPublicFieldEnd
             //sectionPrivateFieldStart
                 private int b;
             //sectionPrivateFieldEnd

             //sectionPublicMethodStart
                 public void test() {}

                 public void testtt() {}
             //sectionPublicMethodEnd

             //sectionPrivateMethodStart
                 private void teste2() {}
             //sectionPrivateMethodEnd
             }

             class Test {}
             //sectionClassEnd
             """, rules);

    doTest("""

             //sectionClassStart
             public class SuperTest {

             //sectionInterfaceStart
                 interface I {}
             //sectionInterfaceEnd

             //sectionEnumStart
                 enum Q {}
             //sectionEnumEnd

             //sectionClassStart
                 class T {}

                 class R {}
             //sectionClassEnd

             //sectionPublicFieldStart
                 public int a;
             //sectionPublicFieldEnd

             //sectionPrivateFieldStart
                 private int b;
             //sectionPrivateFieldEnd

             //sectionPublicMethodStart
                 public void test() {}

                 public void testtt() {}
             //sectionPublicMethodEnd

             //sectionPrivateMethodStart
                 private void teste2() {}
             //sectionPrivateMethodEnd

                 private void newPrivateTest() {}

                 class NewClass {}

                 interface NewInterface {}
             }

             class Test {}
             //sectionClassEnd

             class NewOuterClass {}
             """, """

             //sectionClassStart
             public class SuperTest {

             //sectionInterfaceStart
                 interface I {}
                 interface NewInterface {}
             //sectionInterfaceEnd
             //sectionEnumStart
                 enum Q {}
             //sectionEnumEnd

             //sectionClassStart
                 class T {}

                 class R {}

                 class NewClass {}
             //sectionClassEnd
             //sectionPublicFieldStart
                 public int a;
             //sectionPublicFieldEnd
             //sectionPrivateFieldStart
                 private int b;
             //sectionPrivateFieldEnd

             //sectionPublicMethodStart
                 public void test() {}

                 public void testtt() {}
             //sectionPublicMethodEnd

             //sectionPrivateMethodStart
                 private void teste2() {}

                 private void newPrivateTest() {}
             //sectionPrivateMethodEnd
             }

             class Test {}

             class NewOuterClass {}
             //sectionClassEnd
             """, rules);
  }

  public void test_range_rearrangement() {
    doTest("""

             public class Test {
                 //pubMS
             <range>    public void test1() {}
                 private void test2() {}
                 public void test3() {}</range>
                 //pubME

                 //privMS
                 private void test4() {}
                 //privME
             }
             """, """

             public class Test {
                 //pubMS
                 public void test1() {}

                 public void test3() {}
                 //pubME

                 //privMS
                 private void test2() {}
                 private void test4() {}
                 //privME
             }
             """, List.of(section("//pubMS", "//pubME", rule(PUBLIC, METHOD)),
                          section("//privMS", "//privME", rule(PRIVATE, METHOD))));
  }

  public void test_on_range_with_nested_sections() {
    doTest("""

             //classStart
             public class Test {

                 //classStart
             <range>    class R {}
                 class T {}
                 public void test() {}</range>
                 //classEnd

                 //publicMethodStart
                 public void tester() {}
                 //publicMethodEnd
             }

             class NewOne {
             }
             //classEnd
             """, """

             //classStart
             public class Test {

                 //classStart
                 class R {}
                 class T {}
                 //classEnd

                 //publicMethodStart
                 public void test() {}
                 public void tester() {}
                 //publicMethodEnd
             }

             class NewOne {
             }
             //classEnd
             """, List.of(section("//classStart", "//classEnd", rule(CLASS)),
                          section("//publicMethodStart", "//publicMethodEnd",
                                  rule(PUBLIC, METHOD))));
  }

  //Now comes failing tests - to fix in future

  //TODO look at upper one - it succeeds this is not!!!
  public void do_not_test_on_range_with_three_inner_sections() {
    doTest("""

             //classStart
             public class Test {

                 //classStart
             <range>    class R {}
                 class T {}
                 public void test() {}</range>
                 //classEnd

                 //fieldStart
                 public int i = 1;
                 //fieldEnd

                 //publicMethodStart
                 public void tester() {}
                 //publicMethodEnd
             }

             class NewOne {
             }
             //classEnd
             """, """

             //classStart
             public class Test {

                 //classStart
                 class R {}
                 class T {}
                 //classEnd

                 //fieldStart
                 public int i = 1;
                 //fieldEnd

                 //publicMethodStart
                 public void test() {}
                 public void tester() {}
                 //publicMethodEnd
             }

             class NewOne {
             }
             //classEnd
             """, List.of(section("//classStart", "//classEnd", rule(CLASS)),
                          section("//fieldStart", "//fieldEnd", rule(FIELD)),
                          section("//publicMethodStart", "//publicMethodEnd",
                                  rule(PUBLIC, METHOD))));
  }

  public void do_not_test_field_has_not_only_section_comments() {
    doTest("""
             class Test {

               //method start
               //field1
               public int field1 = 1;

               //method end

               public void method test() {}

             }
             """, """
             class Test {

               //field1
               public int field1 = 1;

               //method start
               public void method test() {}
               //method end

             }
             """, List.of(rule(FIELD),
                          section("//method start", "//method end",
                                  rule(METHOD))));
  }

  public void do_not_test_class_has_not_only_section_comments() {
    doTest("""
             //class start
             //main class
             public class Test {
             }
             //class end

             interface I {
             }

             class A {
             }

             class B {
             }
             """, """
             interface I {
             }

             //class start
             //main class
             public class Test {
             }

             class A {
             }

             class B {
             }
             //class end
             """, List.of(rule(INTERFACE),
                          section("//class start", "//class end",
                                  rule(CLASS))));
  }

  public void do_not_test_method_has_not_only_section_comments() {
    doTest("""
             class Test {

               //methods start
               //first
               public void test() {}

               private void t() {}
               //method end
             }
             """, """
             class Test {

               //methods start
               private void t() {}

               //first
               public void test() {}
               //method end

             }
             """, List.of(
      section("//methods start", "//method end", rule(PRIVATE, METHOD),
              rule(PUBLIC, METHOD))));
  }
}
