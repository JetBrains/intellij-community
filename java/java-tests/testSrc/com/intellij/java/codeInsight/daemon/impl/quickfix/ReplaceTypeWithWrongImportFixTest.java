// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ReplaceTypeWithWrongImportFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testFixNewVariableAsArgument() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p2.B;
                                
                                void main(String[] args) {
                                   call(new A<caret><String>("1"));
                                }
                                
                                void call(B<String> b){}
                                """);
    IntentionAction intention = myFixture.findSingleIntention("Replace a constructor call type with 'bar.p2.A<String>'");
    assertEquals(myFixture.getIntentionPreviewText(intention),
                 """
                   import bar.p1.A;
                   import bar.p2.B;
                   
                   void main(String[] args) {
                      call(new bar.p2.A<String>("1"));
                   }
                   
                   void call(B<String> b){}
                   """);
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                            import bar.p2.A;
                            import bar.p2.B;
                        
                            void main(String[] args) {
                               call(new A<caret><String>("1"));
                            }
                            
                            void call(B<String> b){}
                            """);

  }

  @Test
  public void testFixVariableType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p2.B;
                                
                                void main(String[] args) {
                                    B<? extends Object> b = new A<caret><String>("1");
                                }
                                """);
    myFixture.launchAction(myFixture.findSingleIntention("Replace a variable type with 'bar.p1.B<? extends java.lang.Object>'"));
    myFixture.checkResult("""
                            import bar.p1.A;
                            import bar.p1.B;
                            
                            void main(String[] args) {
                                B<? extends Object> b = new A<String>("1");
                            }
                            """);
  }

  @Test
  public void testFixVariableTypeWithIncompatibleGenerics() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.addClass("""
                         package bar.p3;
                         public class B <T extends Integer>{}
                         """);
    myFixture.addClass("""
                         package bar.p3;
                         public class A<T> extends B<T> {
                             public A(T a) {}
                         }
                         """);
    myFixture.configureByText("B.java",
                              """
                                import bar.p3.A;
                                import bar.p1.B;
                                
                                void main(String[] args) {
                                    B<? extends String> b = new A<caret><String>("1");
                                }
                                """);
    assertThat(myFixture.filterAvailableIntentions("Replace a variable type to 'bar.p3.B<? extends java.lang.String>'")).isNullOrEmpty();
  }

  @Test
  public void testFixVariableTypeWithRawGeneric() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p2.B;
                                
                                void main(String[] args) {
                                    B b = new A<caret><String>("1");
                                }
                                """);
    myFixture.launchAction(myFixture.findSingleIntention("Replace a variable type with 'bar.p1.B<java.lang.String>'"));
    myFixture.checkResult("""
                            import bar.p1.A;
                            import bar.p1.B;
                            
                            void main(String[] args) {
                                B<String> b = new A<String>("1");
                            }
                            """);
  }

  @Test
  public void testFixVariableTypeFromMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p2.B;
                                
                                void main(String[] args) {
                                    B b = get<caret>();
                                }
                                
                                A<String> get(){
                                  return new A<String>("1");
                                }
                                """);
    myFixture.launchAction(myFixture.findSingleIntention("Replace a variable type with 'bar.p1.B<java.lang.String>'"));
    myFixture.checkResult("""
                            import bar.p1.A;
                            import bar.p1.B;
                            
                            void main(String[] args) {
                                B<String> b = get();
                            }
                            
                            A<String> get(){
                              return new A<String>("1");
                            }
                            """);
  }

  @Test
  public void testFixConstructorType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p2.B;
                                
                                void main(String[] args) {
                                    B<? extends Object> b = new A<caret><String>("1");
                                }
                                """);
    myFixture.launchAction(myFixture.findSingleIntention("Replace a constructor call type with 'bar.p2.A<String>'"));
    myFixture.checkResult("""
                            import bar.p2.A;
                            import bar.p2.B;
                            
                            void main(String[] args) {
                                B<? extends Object> b = new A<String>("1");
                            }
                            """);
  }

  @Test
  public void testFixConstructorTypeClassNotInherited() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.addClass("""
                         package bar.p3;
                         public class B <T>{}
                         """);
    myFixture.addClass("""
                         package bar.p3;
                         public class A<T> {
                             public A(T a) {}
                         }
                         """);

    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p3.B;
                                
                                void main(String[] args) {
                                    B<? extends Object> b = new A<caret><String>("1");
                                }
                                """);
    assertThat(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'")).isNullOrEmpty();
  }

  @Test
  public void testFixConstructorTypeClassNotAssignable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.addClass("""
                         package bar.p3;
                         public class B <T>{}
                         """);
    myFixture.addClass("""
                         package bar.p3;
                         public class A<T> extends B<T>{
                             public A(Long a) {}
                         }
                         """);

    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p3.B;
                                
                                void main(String[] args) {
                                    B<? extends Object> b = new A<caret><String>("1");
                                }
                                """);
    assertThat(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'")).isNullOrEmpty();
  }

  @Test
  public void testFixConstructorTypeClassUnavailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.addClass("""
                         package bar.p3;
                         public class B <T>{}
                         """);
    myFixture.addClass("""
                         package bar.p3;
                         class A<T> extends B<T> {
                             A(T a) {}
                         }
                         """);

    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p3.B;
                                
                                void main(String[] args) {
                                    B<? extends Object> b = new A<caret><String>("1");
                                }
                                """);
    assertThat(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'")).isNullOrEmpty();
  }

  @Test
  public void testFixConstructorTypeClassDefaultConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package bar.p3;
                         public class B <T>{}
                         """);
    myFixture.addClass("""
                         package bar.p3;
                         public class A<T> extends B<T> {}
                         """);

    myFixture.addClass("""
                         package bar.p4;
                         public class B <T>{}
                         """);
    myFixture.addClass("""
                         package bar.p4;
                         public class A<T> extends B<T> {}
                         """);

    myFixture.configureByText("B.java",
                              """
                                import bar.p4.A;
                                import bar.p3.B;
                                
                                void main(String[] args) {
                                    B<? extends Object> b = new A<caret><String>();
                                }
                                """);

    myFixture.launchAction(myFixture.findSingleIntention("Replace a constructor call type with 'bar.p3.A<String>'"));
    myFixture.checkResult("""
                            import bar.p3.A;
                            import bar.p3.B;
                            
                            void main(String[] args) {
                                B<? extends Object> b = new A<String>();
                            }
                            """);  }

  @Test
  public void testFixConstructorTypeClassInferredGenerics() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p2.B;
                                
                                void main(String[] args) {
                                    B<String> b = new A<caret><>("2");
                                }
                                """);

    myFixture.launchAction(myFixture.findSingleIntention("Replace a constructor call type with 'bar.p2.A<>'"));
    myFixture.checkResult("""
                            import bar.p2.A;
                            import bar.p2.B;
                            
                            void main(String[] args) {
                                B<String> b = new A<>("2");
                            }
                            """);  }

  @Test
  public void testFixConstructorTypeWithIncompatibleConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.addClass("""
                         package bar.p3;
                         public class B <T>{}
                         """);
    myFixture.addClass("""
                         package bar.p3;
                         public class A<T> extends B<T> {
                             public A(T a, T a2) {}
                         }
                         """);

    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p3.B;
                                
                                void main(String[] args) {
                                    B<? extends Object> b = new A<caret><String>("1");
                                }
                                """);
    assertThat(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'")).isNullOrEmpty();
  }

  @Test
  public void testFixConstructorTypeNoAvailableClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClasses();
    myFixture.addClass("""
                         package bar.p3;
                         public class B <T>{}
                         """);

    myFixture.configureByText("B.java",
                              """
                                import bar.p1.A;
                                import bar.p3.B;
                                
                                void main(String[] args) {
                                    B<? extends Object> b = new A<caret><String>("1");
                                }
                                """);
    assertThat(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'")).isNullOrEmpty();
  }

  private void addClasses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package bar.p2;
                         public class B <T>{}
                         """);
    myFixture.addClass("""
                         package bar.p2;
                         public class A<T> extends B<T> {
                             public A(T a) {}
                         }
                         """);
    myFixture.addClass("""
                         package bar.p1;
                         public class B <T>{}
                         """);
    myFixture.addClass("""
                         package bar.p1;
                         public class A<T> extends B<T> {
                             public A(T a) {}
                         }
                         """);
  }
}
