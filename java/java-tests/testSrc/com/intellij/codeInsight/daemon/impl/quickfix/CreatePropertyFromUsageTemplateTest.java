package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class CreatePropertyFromUsageTemplateTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_template_from_getter() {
    myFixture.configureByText("_.java", """
        class JC<T> {}
        class Main {
          java.util.List<String> usage(JC<String> jc) {
            return jc.<caret>getFoo();
          }
        }""");
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
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
    TemplateManagerImpl.getTemplateState(getEditor()).gotoEnd(false);
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
  }
}
