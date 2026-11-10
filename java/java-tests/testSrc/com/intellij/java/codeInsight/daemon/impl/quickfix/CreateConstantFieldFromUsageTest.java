// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class CreateConstantFieldFromUsageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void test_add_import_when_there_is_a_single_type_variant() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.addClass("package foo; public class Foo { public void someMethod() {} }");
    myFixture.configureByText("a.java", """
      class Test {
        void foo() { <caret>CONST.someMethod(); }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention("Create constant field"));
      myFixture.checkResult("""
                              import foo.Foo;
                              
                              class Test {
                                  private static final <selection>Foo</selection> CONST = ;
                              
                                  void foo() { CONST.someMethod(); }
                              }
                              """);
      assertNull(myFixture.getLookup());
    });
  }

  @Test
  public void test_inside_annotation_argument_with_braces() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      interface A {}
      @SuppressWarnings({A.CON<caret>ST})
      class Test {}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention("Create constant field"));
      myFixture.checkResult("""
                              interface A {
                                  <selection>String</selection> CONST = ;
                              }
                              @SuppressWarnings({A.CONST})
                              class Test {}
                              """);
    });
  }

  @Test
  public void test_inside_annotation_argument_no_braces() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      interface A {}
      @SuppressWarnings(A.CON<caret>ST)
      class Test {}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention("Create constant field"));
      myFixture.checkResult("""
                              interface A {
                                  <selection>String</selection> CONST = ;
                              }
                              @SuppressWarnings(A.CONST)
                              class Test {}
                              """);
    });
  }

  @Test
  public void test_insert_presentable_name_when_showing_type_lookup() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.addClass("package foo; public class Foo { public void someMethod() {} }");
    myFixture.addClass("package bar; public class Bar { public void someMethod() {} }");
    myFixture.configureByText("a.java", """
      class Test {
        void foo() { <caret>CONST.someMethod(); }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention("Create constant field"));
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
    });
  }

  @Test
  public void test_overload_methods_with_single_suggestion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention("Create constant field"));
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
    });
  }

  @Test
  public void test_no_constant_suggestion_when_name_is_not_upper_cased() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Foo {
         {
           B<caret>ar
         }
      }
      """);
    assertThat(myFixture.filterAvailableIntentions("Create constant field")).isNullOrEmpty();
  }
}
