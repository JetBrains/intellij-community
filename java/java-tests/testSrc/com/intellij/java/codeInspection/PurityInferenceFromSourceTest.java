package com.intellij.java.codeInspection;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PurityInferenceFromSourceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void test_getter() {
    assertTransparent("""
                 Object getField() {
                   return field;
                 }""");
  }

  @Test
  public void test_setter() {
    assertImpure("""
                   void setField(String s) {
                     field = s;
                   }""");
  }

  @Test
  public void test_unknown() {
    assertImpure("""
                   int random() {
                     launchMissiles();
                     return 2;
                   }""");
  }

  @Test
  public void test_print() {
    assertImpure("""
                   int random() {
                     System.out.println("hello");
                     return 2;
                   }""");
  }

  @Test
  public void test_local_var_assignment() {
    assertTransparent("""
                 int random(boolean b) {
                   int i = 4;
                   if (b) {
                     i = 2;
                   } else {
                     i++;
                   }
                   return i;
                 }""");
  }

  @Test
  public void test_local_array_var_assignment() {
    assertPure("""
                 int[] randomArray() {
                   int[] i = new int[0];
                   i[0] = random();
                   return i;
                 }
                 int random() { return 2; }""");
  }

  @Test
  public void test_field_array_assignment() {
    assertImpure("""
                   int[] randomArray() {
                     i[0] = random();
                     return i;
                   }
                   int random() { return 2; }
                   int[] i = new int[0];""");
  }

  @Test
  public void test_field_array_assignment_as_local_var() {
    assertImpure("""
                   int[] randomArray() {
                     int[] local = i;
                     local[0] = random();
                     return local;
                   }
                   int random() { return 2; }
                   int[] i = new int[0];""");
  }

  @Test
  public void test_use_explicit_pure_contract() {
    assertPure("""
                 int method() {
                   return smthPure();
                 }
                 @org.jetbrains.annotations.Contract(pure=true) native int smthPure();
                 """);
  }

  @Test
  public void test_don_t_analyze_more_than_one_call() {
    assertImpure("""
                   int method() {
                     return smthPure(smthPure2());
                   }
                   int smthPure(int i) { return i; }
                   int smthPure2() { return 42; }
                   """);
  }

  @Test
  public void test_empty_constructor() {
    assertTransparent("""
                 public Foo() {
                 }""");
  }

  @Test
  public void test_field_writes() {
    assertTransparent("""
                 int x;
                 int y;

                 public Foo() {
                   x = 5;
                   this.y = 10;
                 }""");
  }

  @Test
  public void test_constructor_calling() {
    // IDEA-192251
    assertPure("""
                 private final int i;
                 private final int j;
                 private final Foo a;

                 Foo(int i, Foo a) {
                     this.i = i;
                     this.j = getConstant();
                     this.a = a;
                 }
                 private int getConstant() {return 42;}""");
  }

  @Test
  public void test_delegating_field_writes() {
    assertPure("""
                 int x;
                 int y;

                 public Foo() {
                   this(5, 10);
                 }

                 Foo(int x, int y) {
                   this.x = x;
                   this.y = y;
                 }""");
  }

  @Test
  public void test_delegating_unknown_writes() {
    assertImpure("""
                   int x;
                   int y;

                   public Foo() {
                     this(5, 10);
                   }

                   Foo(int x, int y) {
                     this.x = x;
                     this.z = y;
                   }""");
  }

  @Test
  public void test_static_field_writes() {
    assertImpure("""
                   int x;
                   static int y;

                   public Foo() {
                     x = 5;
                     this.y = 10;
                   }""");
  }

  @Test
  public void test_calling_constructor_with_side_effects() {
    assertImpure("""
                    Object newExample() {
                        return new Example1();
                    }

                    private static int created = 0;

                    Example1() {
                        created++;
                    }""");
  }

  @Test
  public void test_anonymous_class_initializer() {
    assertImpure("""
                    Object smth() {
                        return new I(){{ created++; }};
                    }

                    private static int created = 0;

                    interface I {}""");
  }

  @Test
  public void test_simple_anonymous_class_creation() {
    assertPure("""
                  Object smth() {
                      return new I(){};
                  }

                  interface I {}""");
  }

  @Test
  public void test_anonymous_class_with_constructor_side_effect() {
    assertImpure("""
                    Object smth() {
                        return new I(){};
                    }

                    class I {
                      I() {
                        unknown();
                      }
                    }""");
  }

  @Test
  public void test_anonymous_class_with_arguments() {
    assertImpure("""
                    Object smth() {
                        return new I(unknown()){};
                    }

                    class I {
                      I(int a) {}
                    }""");
  }

  @Test
  public void test_class_with_impure_initializer_creation() {
    assertImpure("""
                    Object smth() {
                        return new I(42);
                    }

                    class I {
                      I(int answer) {}
                      {
                        launchMissiles();
                      }
                    }""");
  }

  @Test
  public void test_class_with_impure_static_initializer_creation() {
    assertPure("""
                  Object smth() {
                      return new I(42);
                  }

                  class I {
                    I(int answer) {}
                    static {
                      launchMissiles();
                    }
                  }""");
  }

  @Test
  public void test_class_with_pure_field_initializers() {
    assertPure("""
                  Object smth() {
                      return new I(42);
                  }

                  class I {
                    int x = 5;
                    I(int answer) {x+=answer;}
                  }""");
  }

  @Test
  public void test_class_with_impure_field_initializers() {
    assertImpure("""
                    Object smth() {
                        return new I(42);
                    }

                    class I {
                      int x = launchMissiles();
                      I(int answer) {x+=answer;}
                    }""");
  }

  @Test
  public void test_class_with_superclass() {
    assertImpure("""
                    Object smth() {
                        return new I(42);
                    }

                    class I extends Foo {
                      // cannot determine purity yet as should delegate to super ctor
                      I(int answer) {}
                    }""");
  }

  @Test
  public void test_delegate_to_a_method_calling_local_class_constructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      class Another {
        static Object method() {
          class LocalClass {
            LocalClass() { launchMissiles(); }
          }
          return new LocalClass();
        }
      }""");
    assertImpure("""
                Object smth() {
                  return Another.method();
                }""");
  }

  @Test
  public void test_increment_field() {
    assertMutatesThis("""   
       int x = 0;
 
       private void increment() {
           x++;
       }
       """);
  }

  @Test
  public void test_delegate_to_setter() {
    assertMutatesThis("""
       int x = 0;

       private void foo() {
           setX(2);
       }

       private void setX(int x) {
           this.x = x;
       }""");
  }

  @Test
  public void test_setter_in_ctor() {
    assertPure("""
        int x = 0;

        public Foo() {
            setX(2);
        }

        private void setX(int x) {
            this.x = x;
        }""");
  }

  @Test
  public void test_plain_field_read() {
    assertTransparent("""
      int x;
    
      int get() {
        return x;
      }""");
  }

  @Test
  public void test_volatile_field_read() {
    assertImpure("""
      volatile int x;
    
      int get() {
        return x;
      }""");
  }

  @Test
  public void test_assertNotNull_is_pure() {
    assertPure("""
                 static void assertNotNull(Object val) {
                   if(val == null) throw new AssertionError();
                 }""");
  }

  @Test
  public void test_recursive_factorial() {
    assertPure("int factorial(int n) { return n == 1 ? 1 : factorial(n - 1) * n;}");
  }

  @Test
  public void test_calling_static_method_with_the_same_signature_in_the_subclass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    RecursionManager.assertOnMissedCache(myFixture.getTestRootDisposable());
    PsiClass clazz = myFixture.addClass("""
      class Super {
        static void foo() { Sub.foo(); }
      }
    
      class Sub extends Super {
        static void foo() {
          unknown();
          unknown2();
        }
      }
      """);
    ReadAction.runBlocking(() -> assertFalse(JavaMethodContractUtil.isPure(clazz.getMethods()[0])));
  }

  @Test
  public void test_super_static_method_does_not_affect_purity() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    RecursionManager.assertOnMissedCache(myFixture.getTestRootDisposable());
    PsiClass clazz = myFixture.addClass("""
      class Sub extends Super {
        static void foo() {
          unknown();
          unknown2();
        }
      }
    
      class Super {
        static void foo() { }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertFalse(JavaMethodContractUtil.isPure(clazz.getMethods()[0]));
      assertTrue(JavaMethodContractUtil.isPure(clazz.getSuperClass().getMethods()[0]));
    });
  }

  @Test
  public void test_enum_method() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("""
      enum X {
        A;
    
        Iterable<?> onXyz() {
          return java.util.Collections.emptyList();
        }
      }
      """);
    ReadAction.runBlocking(() -> assertTrue(JavaMethodContractUtil.isPure(clazz.getMethods()[0])));
  }

  @Test
  public void test_enum_method_with_subclass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("""
      enum X {
        A, B {};
    
        Iterable<?> onXyz() {
          return java.util.Collections.emptyList();
        }
      }
      """);
    ReadAction.runBlocking(() -> assertFalse(JavaMethodContractUtil.isPure(clazz.getMethods()[0])));
  }

  private void assertPure(String classBody) {
    assertMutationSignature(classBody, MutationSignature.pure());
  }

  private void assertTransparent(String classBody) {
    assertMutationSignature(classBody, MutationSignature.transparent());
  }

  private void assertImpure(String classBody) {
    assertMutationSignature(classBody, MutationSignature.unknown());
  }

  private void assertMutatesThis(String classBody) {
    assertMutationSignature(classBody, MutationSignature.pure().alsoMutatesThis());
  }

  private void assertMutationSignature(String classBody, MutationSignature expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("final class Foo { " + classBody + " }");
    ReadAction.runBlocking(() -> {
      assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
      MutationSignature signature = JavaSourceInference.inferMutationSignature((PsiMethodImpl)clazz.getMethods()[0]);
      assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
      assertEquals(expected, signature);
    });
  }
}
