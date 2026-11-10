// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class StaticImportMethodHintTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testTheHintSkipsAGenericMethodWithABrokenTypeBound() {
    addHelperClass("""
                     package p;
                     public final class Numbers {
                       public static <T extends Number> void round(T value) { }
                     }
                     """);
    assertNoHintButIntentionIsAvailable("""
                        class X {
                          void test() {
                            rou<caret>nd("text");
                          }
                        }
                        """);
  }

  @Test
  public void testTheHintShownBoxedTypeBound() {
    addHelperClass("""
                     package p;
                     public final class Numbers {
                       public static <T extends Number> void round(T value) { }
                     }
                     """);
    assertHintShows("""
                        class X {
                          void test() {
                            rou<caret>nd(1);
                          }
                        }
                        """, "round");
  }

  @Test
  public void testTheHintShowsAGenericMethodWithAMatchingTypeBound() {
    addHelperClass("""
                     package p;
                     public final class Printer {
                       public static <T extends CharSequence> void print(T value) { }
                     }
                     """);
    assertHintShows("""
                      class X {
                        void test() {
                          pri<caret>nt("text");
                        }
                      }
                      """, "print");
  }

  @Test
  public void testTheHintShowsAGenericMethodWithABoxedArgument() {
    addHelperClass("""
                     package p;
                     public final class Numbers {
                       public static <T extends Number> void round(T value, String unit) { }
                     }
                     """);
    assertHintShows("""
                      class X {
                        void test() {
                          rou<caret>nd(1, unknownUnit);
                        }
                      }
                      """, "round");
  }

  @Test
  public void testTheHintShowsAGenericMethodWithAnArgumentTheBoundCanCast() {
    addHelperClass("""
                     package p;
                     public final class Numbers {
                       public static <T extends Number> void round(T value) { }
                     }
                     """);
    assertHintShows("""
                      class X {
                        void test(Object value) {
                          rou<caret>nd(value);
                        }
                      }
                      """, "round");
  }

  private void addHelperClass(@Language("JAVA") @NotNull String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(text);
  }

  private void assertNoHintButIntentionIsAvailable(@NotNull String text) {
    assertThat(createFix(text).getHintCandidates()).isNullOrEmpty();
    assertIntentionIsAvailable();
  }

  private void assertHintShows(@NotNull String text, String @NotNull ... names) {
    assertThat(ContainerUtil.map(createFix(text).getHintCandidates(), PsiMethod::getName)).containsOnly(names);
    assertIntentionIsAvailable();
  }

  private void assertIntentionIsAvailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertThat(myFixture.filterAvailableIntentions(QuickFixBundle.message("static.import.method.text"))).isNotEmpty();
  }

  private @NotNull StaticImportMethodFix createFix(@NotNull String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
