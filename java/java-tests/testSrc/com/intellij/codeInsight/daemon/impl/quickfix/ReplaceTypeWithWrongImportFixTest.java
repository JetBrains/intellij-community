// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ReplaceTypeWithWrongImportFixTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_21;
  }

  public void testFixNewVariableAsArgument() {
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

  public void testFixVariableType() {
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

  public void testFixVariableTypeWithIncompatibleGenerics() {
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
    assertEmpty(myFixture.filterAvailableIntentions("Replace a variable type to 'bar.p3.B<? extends java.lang.String>'"));
  }

  public void testFixVariableTypeWithRawGeneric() {
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

  public void testFixVariableTypeFromMethod() {
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

  public void testFixConstructorType() {
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

  public void testFixConstructorTypeClassNotInherited() {
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
    assertEmpty(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'"));
  }

  public void testFixConstructorTypeClassNotAssignable() {
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
    assertEmpty(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'"));
  }

  public void testFixConstructorTypeClassUnavailable() {
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
    assertEmpty(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'"));
  }

  public void testFixConstructorTypeClassDefaultConstructor() {
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

  public void testFixConstructorTypeClassInferredGenerics() {
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

  public void testFixConstructorTypeWithIncompatibleConstructor() {
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
    assertEmpty(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'"));
  }

  public void testFixConstructorTypeNoAvailableClass() {
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
    assertEmpty(myFixture.filterAvailableIntentions("Replace a constructor call type with 'bar.p3.A<String>'"));
  }

  private void addClasses() {
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
