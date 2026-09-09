// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;

public class StaticImportMethodHintTest extends LightJavaCodeInsightFixtureTestCase {
  public void testTheHintSkipsAMethodWithAnotherArgumentCount() {
    addHelperClass("""
                     package p;
                     public final class ComplexMath {
                       public static double distance() { return 0; }
                     }
                     """);
    assertNoHintButIntentionIsAvailable("""
                        class X {
                          void test(double a, double b) {
                            double d = dist<caret>ance(a, b);
                          }
                        }
                        """);
  }

  public void testTheHintShowsAMethodWithTheSameArgumentCount() {
    addHelperClass("""
                     package p;
                     public final class ComplexMath {
                       public static double distance(double a, double b) { return 0; }
                     }
                     """);
    assertHintShows("""
                      class X {
                        void test(double a, double b) {
                          double d = dist<caret>ance(a, b);
                        }
                      }
                      """, "distance");
  }

  public void testTheHintSkipsAMethodWithAnotherParameterType() {
    addHelperClass("""
                     package p;
                     public final class Greeter {
                       public static void greet(String name) { }
                     }
                     """);
    assertNoHintButIntentionIsAvailable("""
                        class X {
                          void test() {
                            gre<caret>et(1);
                          }
                        }
                        """);
  }

  public void testTheHintShowsAMethodWhenTheArgumentTypeIsUnknown() {
    addHelperClass("""
                     package p;
                     public final class Greeter {
                       public static void greet(String name) { }
                     }
                     """);
    assertHintShows("""
                      class X {
                        void test() {
                          gre<caret>et(unknownName);
                        }
                      }
                      """, "greet");
  }

  public void testTheHintShowsAMethodWithALambdaArgument() {
    addHelperClass("""
                     package p;
                     public final class Runner {
                       public static void runIt(Runnable r) { }
                     }
                     """);
    assertHintShows("""
                      class X {
                        void test() {
                          run<caret>It(() -> { });
                        }
                      }
                      """, "runIt");
  }

  public void testTheHintShowsAVarargsMethod() {
    addHelperClass("""
                     package p;
                     public final class Numbers {
                       public static int sum(int... values) { return 0; }
                     }
                     """);
    assertHintShows("""
                      class X {
                        void test() {
                          int s = s<caret>um(1, 2, 3);
                        }
                      }
                      """, "sum");
  }

  public void testTheHintSkipsAVarargsMethodWithTooFewArguments() {
    addHelperClass("""
                     package p;
                     public final class Numbers {
                       public static int sum(int first, int second, int... rest) { return 0; }
                     }
                     """);
    assertNoHintButIntentionIsAvailable("""
                        class X {
                          void test() {
                            int s = s<caret>um(1);
                          }
                        }
                        """);
  }

  public void testTheHintShowsAVarargsMethodWithoutAVarargsArgument() {
    addHelperClass("""
                     package p;
                     public final class Numbers {
                       public static int sum(int first, int second, int... rest) { return 0; }
                     }
                     """);
    assertHintShows("""
                      class X {
                        void test() {
                          int s = s<caret>um(1, 2);
                        }
                      }
                      """, "sum");
  }

  public void testTheHintShowsAGenericMethodWithAMatchingArgument() {
    addHelperClass("""
                     package p;
                     public final class Lists {
                       public static <T> T firstOf(java.util.List<T> list) { return null; }
                     }
                     """);
    assertHintShows("""
                      class X {
                        String test(java.util.List<String> list) {
                          return firs<caret>tOf(list);
                        }
                      }
                      """, "firstOf");
  }

  public void testTheHintSkipsAGenericMethodWithAnotherArgumentCount() {
    addHelperClass("""
                     package p;
                     public final class Lists {
                       public static <T> T firstOf(java.util.List<T> list) { return null; }
                     }
                     """);
    assertNoHintButIntentionIsAvailable("""
                        class X {
                          String test(java.util.List<String> list) {
                            return firs<caret>tOf(list, 1);
                          }
                        }
                        """);
  }

  public void testTheHintSkipsAGenericMethodWhenAConcreteParameterConflicts() {
    addHelperClass("""
                     package p;
                     public final class Maps {
                       public static <T> void put(String key, T value) { }
                     }
                     """);
    assertNoHintButIntentionIsAvailable("""
                        class X {
                          void test() {
                            p<caret>ut(1, "x");
                          }
                        }
                        """);
  }

  public void testTheHintShowsAGenericVarargsMethod() {
    addHelperClass("""
                     package p;
                     public final class Lists {
                       public static <T> java.util.List<T> listOf(T... values) { return null; }
                     }
                     """);
    assertHintShows("""
                      class X {
                        java.util.List<String> test() {
                          return lis<caret>tOf("a", "b");
                        }
                      }
                      """, "listOf");
  }

  /**
   * The check does no type inference, so it accepts an argument which breaks the bound of the type parameter.
   */
  public void testTheHintShowsAGenericMethodWithABrokenTypeBound() {
    addHelperClass("""
                     package p;
                     public final class Numbers {
                       public static <T extends Number> void round(T value) { }
                     }
                     """);
    assertHintShows("""
                      class X {
                        void test() {
                          rou<caret>nd("text");
                        }
                      }
                      """, "round");
  }

  private void addHelperClass(@Language("JAVA") @NotNull String text) {
    myFixture.addClass(text);
  }

  private void assertNoHintButIntentionIsAvailable(@NotNull String text) {
    assertEmpty(createFix(text).getHintCandidates());
    assertIntentionIsAvailable();
  }

  private void assertHintShows(@NotNull String text, String @NotNull ... names) {
    assertSameElements(ContainerUtil.map(createFix(text).getHintCandidates(), PsiMethod::getName), names);
    assertIntentionIsAvailable();
  }

  private void assertIntentionIsAvailable() {
    assertNotEmpty(myFixture.filterAvailableIntentions(QuickFixBundle.message("static.import.method.text")));
  }

  private @NotNull StaticImportMethodFix createFix(@NotNull String text) {
    myFixture.configureByText("X.java", text);
    PsiMethodCallExpression call = PsiTreeUtil.findChildOfType(myFixture.getFile(), PsiMethodCallExpression.class);
    assertNotNull(call);
    try {
      return ApplicationManager.getApplication()
        .executeOnPooledThread(() -> ReadAction.computeBlocking(() -> new StaticImportMethodFix(myFixture.getFile(), call)))
        .get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new AssertionError(e);
    }
  }
}
