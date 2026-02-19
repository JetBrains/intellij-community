// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

public class MagicConstantCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  @NeedsIndex.Full
  public void test_in_method_argument() {
    addModifierList();
    myFixture.configureByText("a.java", """
                                class Foo {
                                  static void foo(ModifierList ml) {
                                    ml.hasModifierProperty(<caret>)
                                  }
                                }
                                """);
    myFixture.complete(CompletionType.SMART);
    myFixture.assertPreferredCompletionItems(0, "PROTECTED", "PUBLIC");
  }

  public void test_nothing_after_dot() {
    addModifierList();
    myFixture.configureByText("a.java", """
                                class Foo {
                                  static void foo(ModifierList ml) {
                                    ml.hasModifierProperty(Foo.<caret>)
                                  }
                                }
                                """);
    assertEquals(0, myFixture.complete(CompletionType.SMART).length);
  }

  public void testMagicConstantInVariableInitializer() {
    addMagicConstant();

    myFixture.configureByText("a.java", """
      class TestAssignment {
          interface States {
              int INIT =0;
              int RUNNING = 1;
              int STOPPED = 2;
          }
          @org.intellij.lang.annotations.MagicConstant(valuesFromClass = States.class)
          int state = <caret>;
          public TestAssignment() {
              this.state = 0;
          }
      }""");

    myFixture.complete(CompletionType.SMART);
    myFixture.assertPreferredCompletionItems(0, "INIT", "RUNNING", "STOPPED");
    LookupManager.getInstance(getProject()).hideActiveLookup();

    myFixture.complete(CompletionType.BASIC);
    myFixture.assertPreferredCompletionItems(0, "INIT", "RUNNING", "STOPPED");
  }

  public void test_magic_constant_in_equality() {
    addMagicConstant();

    myFixture.configureByText("a.java", """
                                class Bar {
                                  static void foo() {
                                    if (getConstant() == <caret>) {}
                                  }

                                  @org.intellij.lang.annotations.MagicConstant(flagsFromClass = Foo.class)
                                  public native int getConstant();
                                }

                                interface Foo {
                                    int FOO = 1;
                                    int BAR = 2;
                                }
                                """);
    myFixture.complete(CompletionType.SMART);
    myFixture.assertPreferredCompletionItems(0, "BAR", "FOO");
    LookupManager.getInstance(getProject()).hideActiveLookup();

    myFixture.complete(CompletionType.BASIC);
    myFixture.assertPreferredCompletionItems(0, "BAR", "FOO");
  }

  @NeedsIndex.ForStandardLibrary
  public void test_completion_in_enum_constructor() {
    addMagicConstant();
    myFixture.configureByText("a.java", """
                                import org.intellij.lang.annotations.MagicConstant;

                                enum MagicConstantTest {
                                  FOO(<caret>),
                                  ;

                                  private final int magicConstant;

                                  MagicConstantTest(@MagicConstant(valuesFromClass = MagicConstantIds.class) int magicConstant) {
                                    this.magicConstant = magicConstant;
                                  }
                                }
                                class MagicConstantIds {
                                  static final int ONE = 1;
                                  static final int TWO = 2;
                                }
                                """);
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "ONE", "TWO");
  }

  public void test_magic_constant_in_equality_before_another_equality() {
    addMagicConstant();

    myFixture.configureByText("a.java", """
                                class Bar {
                                  static void foo() {
                                    if (getConstant() == <caret>getConstant() == 2) {}
                                  }

                                  @org.intellij.lang.annotations.MagicConstant(flagsFromClass = Foo.class)
                                  public native int getConstant();
                                }

                                interface Foo {
                                    int FOO = 1;
                                    int BAR = 2;
                                }
                                """);
    myFixture.complete(CompletionType.SMART);
    myFixture.assertPreferredCompletionItems(0, "BAR", "FOO");
  }

  private void addModifierList() {
    addMagicConstant();

    myFixture.addClass("""
        import org.intellij.lang.annotations.MagicConstant;

        interface PsiModifier {
          @NonNls String PUBLIC = "public";
          @NonNls String PROTECTED = "protected";

          @MagicConstant(stringValues = { PUBLIC, PROTECTED })
          @interface ModifierConstant { }
        }

        interface ModifierList {
          boolean hasModifierProperty(@PsiModifier.ModifierConstant String m) {}
        }
        """);
  }

  private void addMagicConstant() {
    myFixture.addClass("""
        package org.intellij.lang.annotations;
        public @interface MagicConstant {
          String[] stringValues() default {};
          Class<?> flagsFromClass() default void.class;
        }
        """);
  }

  public void testVariableAssignedFromMagicMethodEqEq() {
    addMagicConstant();

    @Language("JAVA") String s = """
        class Bar {
          static void foo() {
            int flags = getConstant();
            if (flags == <caret>) {}
          }

          @org.intellij.lang.annotations.MagicConstant(valuesFromClass = Foo.class)
          public native int getConstant();
        }

        interface Foo {
            int FOO = 1;
            int BAR = 2;
        }
        """;
    myFixture.configureByText("a.java", s);
    myFixture.complete(CompletionType.SMART);
    myFixture.assertPreferredCompletionItems(0, "BAR", "FOO");
  }

  @NeedsIndex.ForStandardLibrary
  public void testVarargMethodCall() {
    addMagicConstant();

    @Language("JAVA") String s = """
        import org.intellij.lang.annotations.MagicConstant;

        public class VarargMethodCall {
          private static class Constants {
            public static final int ONE = 1;
            public static final int TWO = 2;
          }
          public static void testAnnotation2(@MagicConstant(valuesFromClass = Constants.class) int... vars) {
          }
          public static void testMethod() {
            testAnnotation2(<caret>);
          }
        }
        """;
    myFixture.configureByText("a.java", s);
    myFixture.complete(CompletionType.SMART);
    myFixture.assertPreferredCompletionItems(0, "ONE", "TWO");
  }

  @NeedsIndex.ForStandardLibrary
  public void testVarargMethodCall2() {
    addMagicConstant();

    @Language("JAVA") String s =
      """
        import org.intellij.lang.annotations.MagicConstant;

        public class VarargMethodCall {
          private static class Constants {
            public static final int ONE = 1;
            public static final int TWO = 2;
          }
          public static void testAnnotation2(@MagicConstant(valuesFromClass = Constants.class) int... vars) {
          }
          public static void testMethod() {
            testAnnotation2(Constants.ONE, <caret>);
          }
        }
        """;
    myFixture.configureByText("a.java", s);
    myFixture.complete(CompletionType.SMART);
    myFixture.assertPreferredCompletionItems(0, "ONE", "TWO");
  }
}
