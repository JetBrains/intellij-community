package com.intellij.java.codeInspection;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class PurityInferenceFromSourceTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_getter() {
    assertTransparent("""
                 Object getField() {
                   return field;
                 }""");
  }

  public void test_setter() {
    assertImpure("""
                   void setField(String s) {
                     field = s;
                   }""");
  }

  public void test_unknown() {
    assertImpure("""
                   int random() {
                     launchMissiles();
                     return 2;
                   }""");
  }

  public void test_print() {
    assertImpure("""
                   int random() {
                     System.out.println("hello");
                     return 2;
                   }""");
  }

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

  public void test_local_array_var_assignment() {
    assertPure("""
                 int[] randomArray() {
                   int[] i = new int[0];
                   i[0] = random();
                   return i;
                 }
                 int random() { return 2; }""");
  }

  public void test_field_array_assignment() {
    assertImpure("""
                   int[] randomArray() {
                     i[0] = random();
                     return i;
                   }
                   int random() { return 2; }
                   int[] i = new int[0];""");
  }

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

  public void test_use_explicit_pure_contract() {
    assertPure("""
                 int method() {
                   return smthPure();
                 }
                 @org.jetbrains.annotations.Contract(pure=true) native int smthPure();
                 """);
  }

  public void test_don_t_analyze_more_than_one_call() {
    assertImpure("""
                   int method() {
                     return smthPure(smthPure2());
                   }
                   int smthPure(int i) { return i; }
                   int smthPure2() { return 42; }
                   """);
  }

  public void test_empty_constructor() {
    assertTransparent("""
                 public Foo() {
                 }""");
  }

  public void test_field_writes() {
    assertTransparent("""
                 int x;
                 int y;

                 public Foo() {
                   x = 5;
                   this.y = 10;
                 }""");
  }

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

  public void test_static_field_writes() {
    assertImpure("""
                   int x;
                   static int y;

                   public Foo() {
                     x = 5;
                     this.y = 10;
                   }""");
  }

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

  public void test_anonymous_class_initializer() {
    assertImpure("""
                    Object smth() {
                        return new I(){{ created++; }};
                    }

                    private static int created = 0;

                    interface I {}""");
  }

  public void test_simple_anonymous_class_creation() {
    assertPure("""
                  Object smth() {
                      return new I(){};
                  }

                  interface I {}""");
  }

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

  public void test_anonymous_class_with_arguments() {
    assertImpure("""
                    Object smth() {
                        return new I(unknown()){};
                    }

                    class I {
                      I(int a) {}
                    }""");
  }

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

  public void test_delegate_to_a_method_calling_local_class_constructor() {
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

  public void test_increment_field() {
    assertMutatesThis("""   
       int x = 0;
   
       private void increment() {
           x++;
       }
       """);
  }

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

  public void test_plain_field_read() {
    assertTransparent("""
      int x;
      
      int get() {
        return x;
      }""");
  }

  public void test_volatile_field_read() {
    assertImpure("""
      volatile int x;
      
      int get() {
        return x;
      }""");
  }

  public void test_assertNotNull_is_pure() {
    assertPure("""
                 static void assertNotNull(Object val) {
                   if(val == null) throw new AssertionError();
                 }""");
  }

  public void test_recursive_factorial() {
    assertPure("int factorial(int n) { return n == 1 ? 1 : factorial(n - 1) * n;}");
  }

  public void test_calling_static_method_with_the_same_signature_in_the_subclass() {
    RecursionManager.assertOnMissedCache(getTestRootDisposable());
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
    assertFalse(JavaMethodContractUtil.isPure(clazz.getMethods()[0]));
  }

  public void test_super_static_method_does_not_affect_purity() {
    RecursionManager.assertOnMissedCache(getTestRootDisposable());
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
    assertFalse(JavaMethodContractUtil.isPure(clazz.getMethods()[0]));
    assertTrue(JavaMethodContractUtil.isPure(clazz.getSuperClass().getMethods()[0]));
  }

  public void test_enum_method() {
    PsiClass clazz = myFixture.addClass("""
      enum X {
        A;
      
        Iterable<?> onXyz() {
          return java.util.Collections.emptyList();
        }
      }
      """);
    assertTrue(JavaMethodContractUtil.isPure(clazz.getMethods()[0]));
  }

  public void test_enum_method_with_subclass() {
    PsiClass clazz = myFixture.addClass("""
      enum X {
        A, B {};
      
        Iterable<?> onXyz() {
          return java.util.Collections.emptyList();
        }
      }
      """);
    assertFalse(JavaMethodContractUtil.isPure(clazz.getMethods()[0]));
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
    PsiClass clazz = myFixture.addClass("final class Foo { " + classBody + " }");
    assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
    MutationSignature signature = JavaSourceInference.inferMutationSignature((PsiMethodImpl)clazz.getMethods()[0]);
    assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
    assertEquals(expected, signature);
  }
}
