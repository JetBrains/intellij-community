package com.intellij.java.codeInspection;

import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class ContractInferenceFromSourceTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_if_null_return_null() {
    String c = inferContract("""
       String smth(String s) {
         if (s == null) return null;
         return smth();
       }""");
    assertEquals("null -> null", c);
  }

  public void test_if_not_null_return_true() {
    String c = inferContract("""
                                 boolean smth(int a, String s) {
                                   if (s != null) { return true; }
                                   return a == 2;
                                 }""");
    assertEquals("_, !null -> true", c);
  }

  public void test_if_null_fail() {
    String c = inferContract("""
                                 boolean smth(int a, String s) {
                                   if (null == s) { throw new RuntimeException(); }
                                   return a == 2;
                                 }""");
    assertEquals("_, null -> fail", c);
  }

  public void test_if_true_return_the_same() {
    String c = inferContract("""
                                 boolean smth(boolean b, int a) {
                                   if (b) return b;
                                   return a == 2;
                                 }""");
    assertEquals("true, _ -> true", c);
  }

  public void test_if_false_return_negation() {
    String c = inferContract("""
                                 boolean smth(boolean b, int a) {
                                   if (!b) return !(b);
                                   return a == 2;
                                 }""");
    assertEquals("false, _ -> true", c);
  }

  public void test_nested_if() {
    String c = inferContract("""
                                 boolean smth(boolean b, Object o) {
                                   if (!b) if (o != null) return true;
                                   return a == 2;
                                 }""");
    assertEquals("false, !null -> true", c);
  }

  public void test_conjunction() {
    String c = inferContract("""
         boolean smth(boolean b, Object o) {
           if (!b && o != null) return true;
           return a == 2;
         }""");
    assertEquals("false, !null -> true", c);
  }

  public void test_disjunction() {
    List<String> c = inferContracts("""
        boolean smth(boolean b, Object o) {
          if (!b || o != null) return true;
          return a == 2;
        }""");
    assertEquals(Arrays.asList("false, _ -> true", "true, !null -> true"), c);
  }

  public void test_ternary() {
    List<String> c = inferContracts("""
        boolean smth(boolean b, Object o, Object o1) {
          return (!b || o != null) ? true : (o1 != null && o1.hashCode() == 3);
        }""");
    assertEquals(Arrays.asList("false, _, _ -> true", "true, !null, _ -> true", "true, null, null -> false"), c);
  }

  public void test_instanceof() {
    List<String> c = inferContracts("""
                                        boolean smth(Object o) {
                                          return o instanceof String;
                                        }""");
    assertEquals(List.of("null -> false"), c);
  }

  public void test_if_else() {
    List<String> c = inferContracts("""
                                        boolean smth(Object o) {
                                          if (o instanceof String) return false;
                                          else return true;
                                        }""");
    assertEquals(List.of("null -> true"), c);
  }

  public void test_if_return_without_else() {
    List<String> c = inferContracts("""
                                        boolean smth(Object o) {
                                          if (o instanceof String) return false;
                                          return true;
                                        }""");
    assertEquals(List.of("null -> true"), c);
  }

  public void test_if_no_return_without_else() {
    List<String> c = inferContracts("""
                                        boolean smth(Object o) {
                                          if (o instanceof String) callSomething();
                                          return true;
                                        }""");
    assertEquals(List.of("null -> true"), c);
  }

  public void test_assertion() {
    List<String> c = inferContracts("""
                                        boolean smth(Object o) {
                                          assert o instanceof String;
                                          return true;
                                        }""");
    assertEquals(List.of("null -> fail"), c);
  }

  public void test_no_return_value_NotNull_duplication() {
    List<String> c = inferContracts("""
                                        @org.jetbrains.annotations.NotNull String smth(Object o) {
                                          return "abc";
                                        }""");
    assertTrue(c.isEmpty());
  }

  public void test_no_return_value_NotNull_duplication_with_branching() {
    List<String> c = inferContracts("""
                                        @org.jetbrains.annotations.NotNull static Object requireNotNull(Object o) {
                                              if (o == null)
                                                  throw new NullPointerException();
                                              else
                                                  return o;
                                          }""");
    assertEquals(List.of("null -> fail", "!null -> param1"), c);
  }

  public void test_plain_delegation() {
    List<String> c = inferContracts("""
                                        boolean delegating(Object o) {
                                          return smth(o);
                                        }
                                        boolean smth(Object o) {
                                          assert o instanceof String;
                                          return true;
                                        }""");
    assertEquals(List.of("null -> fail"), c);
  }

  public void test_arg_swapping_delegation() {
    List<String> c = inferContracts("""
                                        boolean delegating(Object o, Object o1) {
                                          return smth(o1, o);
                                        }
                                        boolean smth(Object o, Object o1) {
                                          return o == null && o1 != null;
                                        }""");
    assertEquals(List.of("_, !null -> false", "null, null -> false", "!null, null -> true"), c);
  }

  public void test_negating_delegation() {
    List<String> c = inferContracts("""
                                        boolean delegating(Object o) {
                                          return !smth(o);
                                        }
                                        boolean smth(Object o) {
                                          return o == null;
                                        }""");
    assertEquals(List.of("null -> false", "!null -> true"), c);
  }

  public void test_delegation_with_constant() {
    List<String> c = inferContracts("""
                                        boolean delegating(Object o) {
                                          return smth(null);
                                        }
                                        boolean smth(Object o) {
                                          return o == null;
                                        }""");
    assertEquals(List.of("_ -> true"), c);
  }

  public void test_boolean_autoboxing() {
    List<String> c = inferContracts("""
                                      static Object test1(Object o1) {
                                          return o1 == null;
                                      }""");
    assertTrue(c.isEmpty());
  }

  public void test_return_boxed_integer() {
    List<String> c = inferContracts("""
                                       static Object test1(Object o1) {
                                           return o1 == null ? 1 : smth();
                                       }
                                      \s
                                       static native Object smth()
                                      \s""");
    assertEquals(List.of("null -> !null"), c);
  }

  public void test_return_boxed_boolean() {
    List<String> c = inferContracts("""
                                       static Object test1(Object o1) {
                                           return o1 == null ? false : smth();
                                       }
                                      \s
                                       static native Object smth()
                                      \s""");
    assertEquals(List.of("null -> !null"), c);
  }

  public void test_boolean_autoboxing_in_delegation() {
    List<String> c = inferContracts("""
                                       static Boolean test04(String s) {
                                           return test03(s);
                                       }
                                       static boolean test03(String s) {
                                           return s == null;
                                       }
                                      \s""");
    assertTrue(c.isEmpty());
  }

  public void test_boolean_auto_unboxing() {
    List<String> c = inferContracts("""
                                         static boolean test02(String s) {
                                             return test01(s);
                                         }

                                         static Boolean test01(String s) {
                                             if (s == null)
                                                 return new Boolean(false);
                                             else
                                                return null;
                                         }
                                      \s""");
    assertTrue(c.isEmpty());
  }

  public void test_double_constant_auto_unboxing() {
    List<String> c = inferContracts("""
                                         static double method() {
                                           return 1;
                                         }
                                      \s""");
    assertTrue(c.isEmpty());
  }

  public void test_non_returning_delegation() {
    List<String> c = inferContracts("""
                                       static void test2(Object o) {
                                           assertNotNull(o);
                                       }

                                       static boolean assertNotNull(Object o) {
                                           if (o == null) {
                                               throw new NullPointerException();
                                           }
                                           return true;
                                       }
                                      \s""");
    assertEquals(List.of("null -> fail"), c);
  }

  public void test_instanceof_notnull() {
    List<String> c = inferContracts("""
                                       public boolean test2(Object o) {
                                           if (o != null) {
                                               return o instanceof String;
                                           } else {
                                               return test1(o);
                                           }
                                       }
                                       static boolean test1(Object o1) {
                                           return o1 == null;
                                       }
                                      \s""");
    assertTrue(c.isEmpty());
  }

  public void test_no_duplicates_in_delegation() {
    List<String> c = inferContracts("""
                                       static boolean test2(Object o1, Object o2) {
                                           return  test1(o1, o1);
                                       }
                                       static boolean test1(Object o1, Object o2) {
                                           return  o1 != null && o2 != null;
                                       }
                                      \s""");
    assertEquals(List.of("null, _ -> false", "!null, _ -> true"), c);
  }

  public void test_take_explicit_parameter_notnull_into_account() {
    List<String> c = inferContracts("""
                                       final Object foo(@org.jetbrains.annotations.NotNull Object bar) {
                                           if (!(bar instanceof CharSequence)) return null;
                                           return new String("abc");
                                       }
                                      \s""");
    assertTrue(c.isEmpty());
  }

  public void test_skip_empty_declarations() {
    List<String> c = inferContracts("""
                                       final Object foo(Object bar) {
                                           Object o = 2;
                                           if (bar == null) return null;
                                           return new String("abc");
                                       }
                                      \s""");
    assertEquals(List.of("null -> null", "!null -> new"), c);
  }

  public void test_go_inside_do_while() {
    List<String> c = inferContracts("""
                                       final Object foo(Object bar) {
                                           do {
                                             if (bar == null) return null;
                                             bar = smth(bar);
                                           } while (smthElse());
                                           return new String("abc");
                                       }
                                      \s""");
    assertEquals(List.of("null -> null"), c);
  }

  public void test_while_instanceof() {
    List<String> c = inferContracts("""
                                       final Object foo(Object bar) {
                                           while (bar instanceof Smth) bar = ((Smth) bar).getWrapped();\s
                                           return bar;
                                       }
                                      \s
                                       interface Smth {
                                         Object getWrapped();
                                       }
                                      \s""");
    assertEquals(List.of("null -> null"), c);
  }

  public void test_use_invoked_method_notnull() {
    List<String> c = inferContracts("""
                                       final Object foo(Object bar) {
                                           if (bar == null) return null;
                                           return doo();
                                       }

                                       @org.jetbrains.annotations.NotNull Object doo() {}
                                      \s""");
    assertEquals(List.of("null -> null", "!null -> !null"), c);
  }

  public void test_use_delegated_method_notnull() {
    List<String> c = inferContracts("""
                                       final Object foo(Object bar, boolean b) {
                                           return b ? doo() : null;
                                       }

                                       @org.jetbrains.annotations.NotNull Object doo() {}
                                      \s""");
    assertEquals(List.of("_, true -> !null", "_, false -> null"), c);
  }

  public void test_use_delegated_method_notnull_with_contracts() {
    List<String> c = inferContracts("""
                                       final Object foo(Object bar, Object o2) {
                                           return doo(o2);
                                       }

                                       @org.jetbrains.annotations.NotNull Object doo(Object o) {
                                         if (o == null) throw new RuntimeException();
                                         return smth();
                                       }
                                      \s""");
    assertEquals(List.of("_, null -> fail"), c);
  }

  public void test_dig_into_type_cast() {
    List<String> c = inferContracts("""
                                      public static String cast(Object o) {
                                        return o instanceof String ? (String)o : null;
                                      }
                                       \s""");
    assertEquals(List.of("null -> null"), c);
  }

  public void test_string_concatenation() {
    List<String> c = inferContracts("""
                                      public static String test(String s1, String s2) {
                                        return s1 != null ? s1.trim()+s2.trim() : unknown();
                                      }
                                       \s""");
    assertEquals(List.of("!null, _ -> !null"), c);
  }

  public void test_int_addition() {
    List<String> c = inferContracts("""
                                      public static int test(int a, int b) {
                                        return a + b;
                                      }
                                       \s""");
    assertTrue(c.isEmpty());
  }

  public void test_compare_with_string_literal() {
    List<String> c = inferContracts("""
                                      String s(String s) {
                                        return s == "a" ? "b" : null;
                                      }
                                       \s""");
    assertEquals(List.of("null -> null"), c);
  }

  public void test_negative_compare_with_string_literal() {
    List<String> c = inferContracts("""
                                      String s(String s) {
                                        return s != "a" ? "b" : null;
                                      }
                                       \s""");
    assertEquals(List.of("null -> !null"), c);
  }

  public void test_primitive_return_type() {
    List<String> c = inferContracts("""
                                      String s(String s) {
                                        return s != "a" ? "b" : null;
                                      }
                                       \s""");
    assertEquals(List.of("null -> !null"), c);
  }

  public void test_return_after_if_without_else() {
    List<String> c = inferContracts("""
                                      public static boolean isBlank(String s) {
                                              if (s != null) {
                                                  final int l = s.length();
                                                  for (int i = 0; i < l; i++) {
                                                      final char c = s.charAt(i);
                                                      if (c != ' ') {
                                                          return false;
                                                      }
                                                  }
                                              }
                                              return true;
                                          }   \s""");
    assertEquals(List.of("null -> true"), c);
  }

  public void test_do_not_generate_too_many_contract_clauses() {
    List<String> c = inferContracts("""
                                      public static void validate(String p1, String p2, String p3, String p4, String p5, String
                                                  p6, Integer p7, Integer p8, Integer p9, Boolean p10, String p11, Integer p12, Integer p13) {
                                              if (p1 == null && p2 == null && p3 == null && p4 == null && p5 == null && p6 == null && p7 == null && p8 ==
                                                      null && p9 == null && p10 == null && p11 == null && p12 == null && p13 == null)
                                                  throw new RuntimeException();

                                              if (p10 != null && (p8 == null && p7 == null && p9 == null))
                                                  throw new RuntimeException();

                                              if ((p12 != null || p13 != null) && (p12 == null || p13 == null))
                                                  throw new RuntimeException();
                                          }
                                             \s""");
    assertTrue(c.size() <= JavaSourceInference.MAX_CONTRACT_COUNT);// there could be 74 of them in total
  }

  public void test_no_inference_for_unused_anonymous_class_methods_where_annotations_won_t_be_used_anyway() {
    @SuppressWarnings({"ClassInitializerMayBeStatic", "ResultOfObjectAllocationIgnored"}) 
    PsiMethod method = PsiTreeUtil.findChildOfType(myFixture.addClass("""
      class Foo {{
        new Object() {
          Object foo() { return null;}
        };
      }}"""), PsiAnonymousClass.class).getMethods()[0];
    assertTrue(JavaSourceInference.inferContracts((PsiMethodImpl)method).isEmpty());
  }

  public void test_inference_for_used_anonymous_class_methods() {
    @SuppressWarnings({"ClassInitializerMayBeStatic", "ResultOfObjectAllocationIgnored"})
    PsiMethod method = PsiTreeUtil.findChildOfType(myFixture.addClass("""
      class Foo {{
        new Object() {
          Object foo(boolean b) { return b ? null : this;}
          Object bar(boolean b) { return foo(b);}
        };
      }}"""), PsiAnonymousClass.class).getMethods()[0];
    assertEquals(Arrays.asList("true -> null", "false -> this"),
                 ContainerUtil.map(JavaSourceInference.inferContracts((PsiMethodImpl)method), Object::toString));
  }

  public void test_anonymous_class_methods_potentially_used_from_outside() {
    PsiMethod method = PsiTreeUtil.findChildOfType(myFixture.addClass("""
      @SuppressWarnings("ALL")
      class Foo {{
        Runnable r = new Runnable() {
          public void run() {
            throw new RuntimeException();
          }
        };
      }}"""), PsiAnonymousClass.class).getMethods()[0];
    assertEquals(List.of(" -> fail"), ContainerUtil.map(JavaSourceInference.inferContracts((PsiMethodImpl)method), Object::toString));
  }

  public void test_vararg_delegation() {
    List<String> c = inferContracts("""
      boolean delegating(Object o, Object o1) {
        return smth(o, o1);
      }
      boolean smth(Object o, Object... o1) {
        return o == null && o1 != null;
      }""");
    assertEquals(List.of("!null, _ -> false", "null, _ -> true"), c);
  }

  public void test_no_universal_contradictory_contracts_for_nullable_method_delegating_to_notNull() {
    List<String> c = inferContracts("""
      @org.jetbrains.annotations.Nullable\s
      Object delegating() {
        return smth();
      }
      @org.jetbrains.annotations.NotNull\s
      Object smth() {
        return this;
      }""");
    assertTrue(c.isEmpty());
  }

  public void test_nullToEmpty() {
    List<String> c = inferContracts("""
                                        String nullToEmpty(String s) {
                                          return s == null ? "" : s;
                                        }""");
    // NotNull annotation is also inferred, so 'null -> !null' is redundant
    assertEquals(List.of("!null -> param1"), c);
  }

  public void test_coalesce() {
    List<String> c = inferContracts("""
                                        <T> T coalesce(T t1, T t2, T t3) {
                                          if(t1 != null) return t1;
                                          if(t2 != null) return t2;
                                          return t3;
                                        }""");
    assertEquals(List.of("!null, _, _ -> param1", "null, !null, _ -> param2", "null, null, _ -> param3"), c);
  }

  public void test_param_check() {
    List<String> c = inferContracts("""
                                      public static int atLeast(int min, int actual, String varName) {
                                          if (actual < min) throw new IllegalArgumentException('\\\\'' + varName + " must be at least " + min + ": " + actual);
                                          return actual;
                                        }""");
    assertEquals(List.of("_, _, _ -> param2"), c);
  }

  public void test_param_reassigned() {
    List<String> c = inferContracts("""
                                      public static int atLeast(int min, int actual, String varName) {
                                          if (actual < min) throw new IllegalArgumentException('\\\\'' + varName + " must be at least " + min + ": " + actual);
                                          actual+=1;
                                          return actual;
                                        }""");
    assertTrue(c.isEmpty());
  }

  public void test_param_incremented() {
    List<String> c = inferContracts("""
                                      public static int atLeast(int min, int actual, String varName) {
                                          if (actual < min) throw new IllegalArgumentException('\\\\'' + varName + " must be at least " + min + ": " + actual);
                                          System.out.println(++actual);
                                          return actual;
                                        }""");
    assertTrue(c.isEmpty());
  }

  public void test_param_unary_minus() {
    List<String> c = inferContracts("""
                                      public static int atLeast(int min, int actual, String varName) {
                                          if (actual < min) throw new IllegalArgumentException('\\\\'' + varName + " must be at least " + min + ": " + actual);
                                          System.out.println(-actual);
                                          return actual;
                                        }""");
    assertEquals(List.of("_, _, _ -> param2"), c);
  }

  public void test_delegate_to_coalesce() {
    List<String> c = inferContracts("""
                                      public static Object test(Object o1, Object o2) {
                                        return choose(foo(o2, o1), "xyz");
                                      }

                                      @org.jetbrains.annotations.Contract("_, null -> null")\s
                                      public static native Object foo(Object x, Object y);

                                      @org.jetbrains.annotations.Contract("!null, _ -> !null; _, !null -> !null; _, _ -> null")
                                      public static native Object choose(Object o1, Object o2);""");
    assertTrue(c.isEmpty());
  }

  public void test_delegate_to_coalesce_2() {
    List<String> c = inferContracts("""
                                      public static Object test(Object o1, Object o2) {
                                        return choose(o2, foo("xyz", o1));
                                      }

                                      @org.jetbrains.annotations.Contract("_, null -> null")\s
                                      public static native Object foo(Object x, Object y);

                                      @org.jetbrains.annotations.Contract("!null, _ -> !null; _, !null -> !null; _, _ -> null")
                                      public static native Object choose(Object o1, Object o2);""");
    assertEquals(List.of("_, !null -> !null"), c);
  }

  public void test_delegate_to_System_exit() {
    List<String> c = inferContracts("""
                                      public static void test(Object obj, int i) {
                                        System.exit(0);
                                      }""");
    assertEquals(List.of("_, _ -> fail"), c);
  }

  public void test_ternary_two_notnull() {
    List<String> c = inferContracts("""
                                      static String test(String v, boolean b, String s) { return b ? getFoo() : getBar(); }

                                      static String getFoo() { return "foo"; }
                                      static String getBar() { return "bar"; }""");
    assertEquals(List.of("_, _, _ -> !null"), c);
  }

  public void test_not_collapsed() {
    List<String> c = inferContracts("""
                                      static String test(String a, String b) {\s
                                        if(b == null || a == null) return null;
                                        return unknown(a+b); \s
                                      }""");
    assertEquals(List.of("_, null -> null", "null, !null -> null"), c);
  }

  public void test_primitive_cast_ignored() {
    List<String> c = inferContracts("""
                                      static int test(long x) {return (int)x;}""");
    assertTrue(c.isEmpty());
  }

  public void test_return_param_is_not_propagated() {
    List<String> c = inferContracts("""
                                      static String foo(Object x) {return String.class.cast(String.valueOf(x));}""");
    assertTrue(c.isEmpty());
  }

  public void test_return_param_propagated() {
    List<String> c = inferContracts("""
                                      static Object foo(Class<?> c, Object x) {return c.cast(x);}""");
    assertEquals(List.of("_, _ -> param2"), c);
  }

  public void test_return_param_propagated_to_this() {
    List<String> c = inferContracts("""
                                      Object foo(Class<?> c) {return c.cast(this);}""");
    assertEquals(List.of("_ -> this"), c);
  }

  public void test_return_param_propagated_new() {
    List<String> c = inferContracts("""
                                      static Object foo() {return java.util.Objects.requireNonNull(new Object());}""");
    assertEquals(List.of(" -> new"), c);
  }

  public void test_this_not_propagated() {
    List<String> c = inferContracts("""
                                      StringBuilder foo() {return new StringBuilder().append("foo");}""");
    assertEquals(List.of(" -> new"), c);
  }

  public void test_this_propagated_to_parameter() {
    List<String> c = inferContracts("""
                                      StringBuilder foo(StringBuilder sb) {return sb.append("foo");}""");
    assertEquals(List.of("_ -> param1"), c);
  }

  public void test_boxed_boolean_equals() {
    List<String> c = inferContracts("""
                                      public static boolean isFalse(@Nullable Boolean condition) {
                                              return Boolean.FALSE.equals(condition);
                                          }""");
    assertEquals(List.of("false -> true", "true -> false", "null -> false"), c);
  }

  public void test_boxed_boolean_equals1() {
    List<String> c = inferContracts("""
                                      public static boolean isFalse(boolean condition) {
                                              return Boolean.TRUE.equals(condition);
                                          }""");
    assertEquals(List.of("true -> true", "false -> false"), c);
  }

  public void test_boxed_boolean_equals2() {
    List<String> c = inferContracts("""
                                      public static boolean isFalse(@Nullable Boolean condition) {
                                              return Boolean.TRUE.equals(condition);
                                          }""");
    assertEquals(List.of("true -> true", "false -> false", "null -> false"), c);
  }

  public void test_boxed_boolean_equals3() {
    List<String> c = inferContracts("""
                                       \s
                                          final boolean test(Foo x) {
                                            return Boolean.TRUE.equals(x.getBoolean());
                                          }

                                          private native Boolean getBoolean();""");
    assertTrue(c.isEmpty());
  }

  private String inferContract(String method) {
    return UsefulTestCase.assertOneElement(inferContracts(method));
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }

  private List<String> inferContracts(String method) {
    PsiClass clazz = myFixture.addClass("final class Foo { " + method + " }");
    assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
    List<StandardMethodContract> contracts = JavaSourceInference.inferContracts((PsiMethodImpl)clazz.getMethods()[0]);
    assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
    return ContainerUtil.map(contracts, Object::toString);
  }
}
