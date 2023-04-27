// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;

import java.util.List;

public class AddImportActionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testMap15() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_5, () -> {
      myFixture.configureByText("a.java", """
        public class Foo {
            void foo() {
                Ma<caret>p l;
            }
        }
        """);
      importClass();
      myFixture.checkResult("""
                              import java.util.Map;

                              public class Foo {
                                  void foo() {
                                      Ma<caret>p l;
                                  }
                              }
                              """);
    });
  }

  public void testMapLatestLanguageLevel() {
    myFixture.configureByText("a.java", """
      public class Foo {
          void foo() {
              Ma<caret>p l;
          }
      }
      """);
    importClass();
    myFixture.checkResult("""
                            import java.util.Map;

                            public class Foo {
                                void foo() {
                                    Ma<caret>p l;
                                }
                            }
                            """);
  }

  public void testStringValue() {
    myFixture.addClass("package java.lang; class StringValue {}");
    myFixture.addClass("package foo; public class StringValue {}");
    myFixture.configureByText("a.java", """
      public class Foo {
          String<caret>Value sv;
      }
      """);
    importClass();
    myFixture.checkResult("""
                            import foo.StringValue;

                            public class Foo {
                                String<caret>Value sv;
                            }
                            """);
  }

  public void testInaccessibleInnerInSuper() {
    myFixture.addClass("package foo; class Super { private class Inner {}}");
    myFixture.configureByText("a.java", """
      package foo;
      public class Foo {
          In<caret>ner in;
      }
      """);
    assertTrue(myFixture.filterAvailableIntentions("Import class").isEmpty());
  }

  public void testUncommentInnerClass() {
    myFixture.addClass("package bar; public class FooBar {}");
    PsiFile file =
      myFixture.configureByText("a.java", """
        package foo;
        public class Foo {
            Foo<caret>Bar fb;
            //class FooBar {}
        }
        """);

    List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Import class");
    assertTrue(intentions.size() > 0);

    int commentOffset = file.getText().indexOf("//");
    myFixture.getEditor().getCaretModel().moveToOffset(commentOffset);
    LightPlatformCodeInsightTestCase.delete(myFixture.getEditor(), myFixture.getProject());
    LightPlatformCodeInsightTestCase.delete(myFixture.getEditor(), myFixture.getProject());
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    assertFalse(intentions.get(0).isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
  }

  public void testPackageLocalInner() {
    myFixture.addClass("package foo; class Outer { static class Inner {static String FOO = \"\";}}");

    myFixture.configureByText("a.java", """
      package foo;
      class Usage {
        {
          String foo = In<caret>ner.FOO;
        }
      }
      """);

    importClass();

    myFixture.checkResult("""
                            package foo;
                            class Usage {
                              {
                                String foo = Outer.Inner.FOO;
                              }
                            }
                            """);
  }

  public void testWrongTypeParams() {
    myFixture.addClass("package f; public class Foo {}");
    myFixture.configureByText("a.java", """
      public class Bar {
        Fo<caret>o<String> foo;
      }
      """);
    importClass();
    myFixture.checkResult("""
                            import f.Foo;

                            public class Bar {
                              Foo<String> foo;
                            }
                            """);
  }

  public void testUseContext() {
    myFixture.addClass("package foo; public class Log {}");
    myFixture.addClass("package bar; public class Log {}");
    myFixture.addClass("package bar; public class LogFactory { public static Log log(){} }");
    myFixture.configureByText("a.java", """
      public class Foo {
          Lo<caret>g l = bar.LogFactory.log();
      }
      """);
    importClass();
    myFixture.checkResult("""
                            import bar.Log;

                            public class Foo {
                                Lo<caret>g l = bar.LogFactory.log();
                            }
                            """);
  }

  public void test_use_initializer() {
    myFixture.addClass("package foo; public class Map {}");
    myFixture.configureByText("a.java", """
      import java.util.HashMap;

      public class Foo {
          Ma<caret>p l = new HashMap<>();
      }
      """);
    ImportClassFix intention =
      (ImportClassFix)IntentionActionDelegate.unwrap(myFixture.findSingleIntention("Import class"));
    assertTrue(ContainerUtil.exists(intention.getClassesToImport(), cls -> cls.getQualifiedName().equals(
      CommonClassNames.JAVA_UTIL_MAP)));
  }

  public void testUseOverrideContext() {
    myFixture.addClass("package foo; public class Log {}");
    myFixture.addClass("package bar; public class Log {}");
    myFixture.addClass("package goo; public class Super { public void foo(foo.Log log) {} }");
    myFixture.configureByText("a.java", """
      public class Foo extends goo.Super {
          @Override
          public void foo(Log<caret> log) {}
      }
      """);
    importClass();
    myFixture.checkResult(
      """
        import foo.Log;

        public class Foo extends goo.Super {
            @Override
            public void foo(Log<caret> log) {}
        }
        """);
  }

  public void testImportFoldingWithConflicts() {

    myFixture.addClass("package p1; public class B {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");

    myFixture.addClass("package p2; public class B {}");
    myFixture.configureByText("C.java",
                              """
                                package p2;

                                import p1.A1;
                                import p1.A2;
                                import p1.A3;
                                import p1.A4;
                                import p1.B;

                                class C {

                                     A1 a1;
                                     A2 a2;
                                     A3 a3;
                                     A4 a4;
                                     A<caret>5 a5;

                                     B b;
                                }

                                """);
    importClass();

    myFixture.checkResult(
      """
        package p2;

        import p1.*;
        import p1.B;

        class C {

             A1 a1;
             A2 a2;
             A3 a3;
             A4 a4;
             A5 a5;

             B b;
        }

        """);
  }

  public void testImportFoldingWithConflictsToJavaLang() {

    myFixture.addClass("package p1; public class String {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");

    myFixture.configureByText("C.java",
                              """
                                package p2;

                                import p1.A1;
                                import p1.A2;
                                import p1.A3;
                                import p1.A4;

                                class C {

                                     A1 a1;
                                     A2 a2;
                                     A3 a3;
                                     A4 a4;
                                     A<caret>5 a5;

                                     String myName;
                                }

                                """);
    importClass();

    myFixture.checkResult(
      """
        package p2;

        import p1.*;

        import java.lang.String;

        class C {

             A1 a1;
             A2 a2;
             A3 a3;
             A4 a4;
             A5 a5;

             String myName;
        }

        """);
  }

  public void testAnnotatedImport() {
    myFixture.addClass(
      ("""

               import java.lang.annotation.*;
               @Target(ElementType.TYPE_USE) @interface TA { }\
         """).stripIndent().trim());

    myFixture.configureByText("a.java",
                              ("""

                                       class Test {
                                           @TA
                                           public <caret>Collection<@TA String> c;
                                       }\
                                 """).stripIndent()
                                .trim());

    importClass();

    myFixture.checkResult(
      ("""

               import java.util.Collection;

               class Test {
                   @TA
                   public <caret>Collection<@TA String> c;
               }\
         """).stripIndent()
        .trim());
  }

  public void testAnnotatedQualifiedImport() {
    myFixture.addClass(
      ("""

               import java.lang.annotation.*;
               @Target(ElementType.TYPE_USE) @interface TA { }\
         """).stripIndent().trim());

    myFixture.configureByText("a.java",
                              ("""

                                       class Test {
                                           java.u<caret>til.@TA Collection<@TA String> c;
                                       }\
                                 """).stripIndent()
                                .trim());

    reimportClass();

    myFixture.checkResult(
      ("""

               import java.util.Collection;

               class Test {
                   <caret>@TA Collection<@TA String> c;
               }\
         """).stripIndent()
        .trim());
  }

  public void testUnresolvedAnnotatedImport() {
    myFixture.configureByText("a.java", ("""

                                                 class Test {
                                                     @Nullable Collection<caret> c;
                                                 }\
                                           """).stripIndent().trim());

    importClass();

    myFixture.checkResult(
      ("""

               import java.util.Collection;

               class Test {
                   @Nullable
                   Collection<caret> c;
               }\
         """).stripIndent()
        .trim());
  }

  public void test_import_class_in_class_reference_expression() {
    myFixture.configureByText("a.java", """

      class Test {
          {
            equals(Co<caret>llection.class);
          }
      }
      """);
    importClass();
    myFixture.checkResult("""
                            import java.util.Collection;

                            class Test {
                                {
                                  equals(Co<caret>llection.class);
                                }
                            }
                            """);
  }

  public void test_import_class_in_qualifier_expression() {
    myFixture.configureByText("a.java", """

      class Test {
          {
            equals(Co<caret>llections.emptySet());
          }
      }
      """);
    importClass();
    myFixture.checkResult("""
                            import java.util.Collections;

                            class Test {
                                {
                                  equals(Co<caret>llections.emptySet());
                                }
                            }
                            """);
  }

  public void test_don_t_import_class_in_method_call_argument() {
    myFixture.configureByText("a.java", """

      class Test {
          {
            equals(Co<caret>llection);
          }
      }
      """);
    assertTrue(myFixture.filterAvailableIntentions("Import class").isEmpty());
  }

  public void test_don_t_import_class_if_qualified_name_is_not_valid() {
    myFixture.addClass("\npackage a..p;\npublic class MMM {}\n");
    myFixture.configureByText("a.java", """

      class Test {
          {
            MM<caret>M m;
          }
      }
      """);
    importClass();
    myFixture.checkResult("""
                            import a.MMM;

                            class Test {
                                {
                                  MMM m;
                                }
                            }
                            """);
  }

  public void test_don_t_import_class_in_assignment() {
    myFixture.configureByText("a.java", """

      class Test {
          {
            Co<caret>llection = 2;
          }
      }
      """);
    assertTrue(myFixture.filterAvailableIntentions("Import class").isEmpty());
  }

  public void test_don_t_import_class_if_already_imported_but_not_accessible() {
    myFixture.addClass("\npackage foo;\nclass Foo {}\n");
    myFixture.configureByText("a.java", """

      import foo.Foo;
      class Test {
          {
            F<caret>oo\s
          }
      }
      """);
    assertTrue(myFixture.filterAvailableIntentions("Import class").isEmpty());
  }

  public void test_don_t_import_class_in_qualified_reference_at_reference_name() {
    myFixture.configureByText("a.java", """

      class Test {
          {
            Test.Te<caret>st
          }
      }
      """);
    assertTrue(myFixture.filterAvailableIntentions("Import class").isEmpty());
  }

  public void test_don_t_import_class_in_qualified_reference_at_foreign_place() {
    myFixture.configureByText("a.java",
                              """

                                class Test {
                                    {
                                      String s = "";
                                      s.<caret>
                                      String p = "";
                                    }
                                }
                                """);
    assertTrue(myFixture.filterAvailableIntentions("Import class").isEmpty());
  }

  public void test_allow_to_add_import_from_javadoc() {
    myFixture.configureByText("a.java", """

      class Test {

        /**
         * {@link java.uti<caret>l.Map}
         */
        void run() {
        }
      }
      """);
    reimportClass();
    myFixture.checkResult("import java.util.Map;\n\nclass Test {\n\n  /**\n   * {@link Map}\n   */\n  void run() {\n  }\n}\n");
  }

  public void test_do_not_add_import_for_default_package() {
    myFixture.configureByText("a.java", """

      class Test {

        /**
         * {@link java.l<caret>ang.Math}
         */
        void run() {
        }
      }
      """);
    myFixture.enableInspections(new UnnecessaryFullyQualifiedNameInspection());
    myFixture.launchAction(myFixture.findSingleIntention("Remove unnecessary qualification"));
    myFixture.checkResult("""

                            class Test {

                              /**
                               * {@link Math}
                               */
                              void run() {
                              }
                            }
                            """);
  }

  public void test_do_not_allow_to_add_import_in_package_info_file() {
    myFixture.configureByText("package-info.java", """


      /**
       * {@link java.lang.Ma<caret>th}
       */
      package com.rocket.test;
      """);
    assertTrue(myFixture.filterAvailableIntentions("Replace qualified name").isEmpty());
  }

  public void test_do_not_allow_to_add_import_on_inaccessible_class() {
    myFixture.addClass("package foo; class Foo {}");
    myFixture.configureByText("A.java", """

      package a;
      /**
       * {@link foo.Fo<caret>o}
       */
      class A {}
      """);
    myFixture.enableInspections(new UnnecessaryFullyQualifiedNameInspection());
    assertTrue(myFixture.filterAvailableIntentions("Replace qualified name").isEmpty());
  }

  public void test_keep_methods_formatting_on_add_import() {
    CommonCodeStyleSettings settings =
      CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean old = settings.ALIGN_GROUP_FIELD_DECLARATIONS;
    settings.ALIGN_GROUP_FIELD_DECLARATIONS = true;

    try {
      myFixture.configureByText("Tq.java",
                                """
                                    
                                  class Tq {
                                    
                                      private Li<caret>st<String> test = null;
                                    
                                      private String varA = "AAA";
                                      private String varBLonger = "BBB";
                                    
                                    
                                      public String getA         () { return varA;       }
                                    
                                      public String getVarBLonger() { return varBLonger; }
                                    
                                  }
                                  """);
      importClass();
      myFixture.checkResult(
        """
          import java.util.List;
            
          class Tq {
            
              private List<String> test = null;
            
              private String varA = "AAA";
              private String varBLonger = "BBB";
            
            
              public String getA         () { return varA;       }
            
              public String getVarBLonger() { return varBLonger; }
            
          }
          """);
    }
    finally {
      settings.ALIGN_GROUP_FIELD_DECLARATIONS = old;
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private void importClass() {
    myFixture.launchAction(myFixture.findSingleIntention("Import class"));
  }

  private void reimportClass() {
    myFixture.enableInspections(new UnnecessaryFullyQualifiedNameInspection());
    myFixture.launchAction(myFixture.findSingleIntention("Replace qualified name with import"));
  }

  public void test_disprefer_deprecated_classes() {
    myFixture.addClass("package foo; public class Log {}");
    myFixture.addClass("package bar; @Deprecated public class Log {}");
    myFixture.configureByText("a.java", """
      public class Foo {
          Lo<caret>g l;
      }
      """);
    importClass();
    myFixture.checkResult("""
                            import foo.Log;

                            public class Foo {
                                Lo<caret>g l;
                            }
                            """);
  }

  public void test_prefer_from_imported_package() {
    myFixture.addClass("package foo; public class Log {}");
    myFixture.addClass("package foo; public class Imported {}");
    myFixture.addClass("package bar; public class Log {}");
    myFixture.configureByText("a.java", """
      import foo.Imported;
      public class Foo {
          Lo<caret>g l;
          Imported i;
      }
      """);
    importClass();
    myFixture.checkResult("""
                            import foo.Imported;
                            import foo.Log;

                            public class Foo {
                                Lo<caret>g l;
                                Imported i;
                            }
                            """);
  }

  public void test_remember_chosen_variants() {
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());
    myFixture.addClass("package foo; public class Log {}");
    myFixture.addClass("package bar; public class Log {}");

    String textBefore = """

      public class Foo {
          Lo<caret>g l;
      }
      """;
    String textAfter = "import bar.Log;\n\npublic class Foo {\n    Lo<caret>g l;\n}\n";
    myFixture.configureByText("a.java", textBefore);
    importClass();
    myFixture.checkResult(textAfter);

    myFixture.addClass("package aPackage; public class Log {}");
    myFixture.configureByText("b.java", textBefore);
    importClass();
    myFixture.checkResult(textAfter);
  }

  public void test_incomplete_method_returning_its_type_parameter() {
    myFixture.addClass("package foo; public class Context {}");
    myFixture.configureByText("a.java", """

      class Foo {
        <Context> java.util.ArrayList<Contex<caret>t> abc
      } \s
      """);
    assertTrue(myFixture.filterAvailableIntentions("Import class").isEmpty());
  }

  public void test_even_more_incomplete_method_returning_its_type_parameter() {
    myFixture.addClass("package foo; public class Context {}");
    myFixture.configureByText("a.java", """

      class Foo {
        <Context> Contex<caret>t
      } \s
      """);
    assertTrue(myFixture.filterAvailableIntentions("Import class").isEmpty());
  }

  public void test_inaccessible_class_from_the_project() {
    myFixture.addClass("package foo; class Foo {}");
    myFixture.configureByText("a.java", """

      class Bar {
        F<caret>oo abc;
      } \s
      """);
    assertFalse(myFixture.filterAvailableIntentions("Import class").isEmpty());
  }

  public void test_prefer_top_level_List() {
    myFixture.addClass("package foo; public interface Restore { interface List {}}");

    myFixture.configureByText("a.java", "class F implements Lis<caret>t {}");
    importClass();
    myFixture.checkResult("""
                            import java.util.List;

                            class F implements List {}""");
  }

  public void test_type_use_annotation() {
    myFixture.addClass("@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface AssertTrue {}");
    myFixture.configureByText("test.java",
                              """
                                import java.util.List;
                                class IntellijBugTest {
                                    final List<?> list = new @AssertTrue Array<caret>List<Object>();
                                }""");
    importClass();
    myFixture.checkResult(
      """
        import java.util.ArrayList;
        import java.util.List;
        class IntellijBugTest {
            final List<?> list = new @AssertTrue ArrayList<Object>();
        }""");
  }
}
