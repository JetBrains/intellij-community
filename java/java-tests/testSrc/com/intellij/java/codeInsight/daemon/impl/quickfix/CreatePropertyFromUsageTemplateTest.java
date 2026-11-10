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

@TestApplication
public class CreatePropertyFromUsageTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void test_template_from_getter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.java", """
        class JC<T> {}
        class Main {
          java.util.List<String> usage(JC<String> jc) {
            return jc.<caret>getFoo();
          }
        }""");
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention("Create property 'foo' in 'JC'"));
      // check initial template
      myFixture.checkResult("""
                              import java.util.List;

                              class JC<T> {
                                  public <selection>List</selection><T> getFoo() {
                                      return foo;
                                  }

                                  public void setFoo(List<T> foo) {
                                      this.foo = foo;
                                  }
                              }
                              class Main {
                                java.util.List<String> usage(JC<String> jc) {
                                  return jc.getFoo();
                                }
                              }""");
      // type into getter type template window
      myFixture.type("Foo\t");
      // check that setter type changes too
      myFixture.checkResult("""
                              import java.util.List;

                              class JC<T> {
                                  public Foo<<selection>T</selection>> getFoo() {
                                      return foo;
                                  }

                                  public void setFoo(Foo<T> foo) {
                                      this.foo = foo;
                                  }
                              }
                              class Main {
                                java.util.List<String> usage(JC<String> jc) {
                                  return jc.getFoo();
                                }
                              }""");
      // go to getter name reference and change it to bar
      myFixture.type("\tbar");
      // check that setter name reference changes too
      myFixture.checkResult("""
                              import java.util.List;

                              class JC<T> {
                                  public Foo<T> getFoo() {
                                      return bar<caret>;
                                  }

                                  public void setFoo(Foo<T> foo) {
                                      this.bar = foo;
                                  }
                              }
                              class Main {
                                java.util.List<String> usage(JC<String> jc) {
                                  return jc.getFoo();
                                }
                              }""");
      // go to setter parameter name and it to param
      myFixture.type("\tparam");
      myFixture.checkResult("""
                              import java.util.List;

                              class JC<T> {
                                  public Foo<T> getFoo() {
                                      return bar;
                                  }
                              
                                  public void setFoo(Foo<T> param<caret>) {
                                      this.bar = param;
                                  }
                              }
                              class Main {
                                java.util.List<String> usage(JC<String> jc) {
                                  return jc.getFoo();
                                }
                              }""");
      TemplateManagerImpl.getTemplateState(myFixture.getEditor()).gotoEnd(false);
      // check that new field is created with user-defined type and name
      myFixture.checkResult("""
                              import java.util.List;

                              class JC<T> {
                                  private Foo<T> bar;

                                  public Foo<T> getFoo() {<caret>
                                      return bar;
                                  }

                                  public void setFoo(Foo<T> param) {
                                      this.bar = param;
                                  }
                              }
                              class Main {
                                java.util.List<String> usage(JC<String> jc) {
                                  return jc.getFoo();
                                }
                              }""");
    });
  }
}
