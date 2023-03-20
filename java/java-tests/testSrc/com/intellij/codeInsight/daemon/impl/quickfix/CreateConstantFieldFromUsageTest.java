// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class CreateConstantFieldFromUsageTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_add_import_when_there_is_a_single_type_variant() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.addClass("package foo; public class Foo { public void someMethod() {} }");
    myFixture.configureByText("a.java", "\nclass Test {\n  void foo() { <caret>CONST.someMethod(); }\n}\n");
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"));
    myFixture.checkResult("""
                            import foo.Foo;

                            class Test {
                                private static final <selection>Foo</selection> CONST = ;

                                void foo() { CONST.someMethod(); }
                            }
                            """);
    assertNull(myFixture.getLookup());
  }

  public void test_inside_annotation_argument_with_braces() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "\ninterface A {}\n@SuppressWarnings({A.CON<caret>ST})\nclass Test {}\n");
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"));
    myFixture.checkResult("""
                            
                            interface A {
                                <selection>String</selection> CONST = ;
                            }
                            @SuppressWarnings({A.CONST})
                            class Test {}
                            """);
  }

  public void test_inside_annotation_argument_no_braces() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      interface A {}
      @SuppressWarnings(A.CON<caret>ST)
      class Test {}
      """);
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"));
    myFixture.checkResult("""
                            interface A {
                                <selection>String</selection> CONST = ;
                            }
                            @SuppressWarnings(A.CONST)
                            class Test {}
                            """);
  }

  public void test_insert_presentable_name_when_showing_type_lookup() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.addClass("package foo; public class Foo { public void someMethod() {} }");
    myFixture.addClass("package bar; public class Bar { public void someMethod() {} }");
    myFixture.configureByText("a.java", "class Test {\n  void foo() { <caret>CONST.someMethod(); }\n}\n");
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"));
    myFixture.checkResult("""
        import bar.Bar;

        class Test {
            private static final <selection>Bar</selection> CONST = ;

            void foo() { CONST.someMethod(); }
        }
        """);
    assertNotNull(myFixture.getLookup());
    myFixture.type("\n");
    myFixture.checkResult("""
        import bar.Bar;

        class Test {
            private static final Bar CONST = <caret>;

            void foo() { CONST.someMethod(); }
        }
        """);
  }

  public void test_overload_methods_with_single_suggestion() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
                                class Foo {}
                                class Test {
                                    {
                                        foo(new Foo(), <caret>BAR);
                                    }

                                    void foo(Foo f, String d) {}
                                    void foo(Foo f, String d, String d2) {}

                                }
                                """);
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"));
    myFixture.checkResult("""
        class Foo {}
        class Test {
            private static final String BAR = ;

            {
                foo(new Foo(), BAR);
            }

            void foo(Foo f, String d) {}
            void foo(Foo f, String d, String d2) {}

        }
        """);
  }

  public void test_no_constant_suggestion_when_name_is_not_upper_cased() {
    myFixture.configureByText("a.java", """
      class Foo {
         {
           B<caret>ar
         }
      }
      """);
    UsefulTestCase.assertEmpty(myFixture.filterAvailableIntentions("Create constant field"));
  }
}
