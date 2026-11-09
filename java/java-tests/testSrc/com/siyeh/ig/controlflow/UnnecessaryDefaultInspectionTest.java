// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class UnnecessaryDefaultInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), getEnvironmentClasses());

  @Test
  public void testUnnecessaryDefault() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testNestedDeconstructionPatterns() {
    inspectionFixture.get().doTest("""
    final class Test {
        public static void main(String[] args) {
            final var nullLevel2 = new Level1(null);
            final var nullLevel3 = new Level1(new Level2(null));
            final var full = new Level1(new Level2(new Level3()));
            System.out.println(getLevel3(null));
            System.out.println(getLevel3(nullLevel2));
            System.out.println(getLevel3(nullLevel3));
            System.out.println(getLevel3(full));
        }
    
        private static Level3 getLevel3(final Level1 level1) {
            return switch (level1) {
                case Level1(Level2(var something)) -> something;
                case null, default -> null;
            };
        }
    
        static class Level3 {
        }
    
        record Level2(Level3 something) {
        }
    
        record Level1(Level2 level2) {
        }
    }""");
  }

  @Test
  public void testSwitchExpression() {
    inspectionFixture.get().doTest("""
             class X {
               boolean x(E e) {
                 return switch (e) {
                   case A, B -> true;
                   /*'default' branch is unnecessary*//*_*/default/**/ -> false;
                 };
               }
             }""");
    inspectionFixture.get().checkQuickFix("Remove 'default' branch",
                  """
                    class X {
                      boolean x(E e) {
                        return switch (e) {
                          case A, B -> true;
                        };
                      }
                    }""");
  }

  @Test
  public void testSwitchFallthrough() {
    inspectionFixture.get().doTest("""
             class X {
               void x(E e) {
                 switch (e) {
                   case A,B:
                       System.out.println(e);
                   /*'default' branch is unnecessary*/default/*_*//**/:
                       System.out.println();
                 }
               }
             }
             """);
    inspectionFixture.get().checkQuickFix("Remove 'default' branch",
                  """
                    class X {
                      void x(E e) {
                        switch (e) {
                          case A,B:
                              System.out.println(e);
                              System.out.println();
                        }
                      }
                    }
                    """);
  }

  @Test
  public void testDeclarationInBranch() {
    inspectionFixture.get().doTest("class X {" +
           "  void x(E e) {" +
           "    switch (e) {" +
           "      /*'default' branch is unnecessary*/default/*_*//**/:" +
           "        int x = 1;" +
           "        System.out.println(x);" +
           "      case A,B:" +
           "        x = 2;" +
           "        System.out.println(x);" +
           "      }" +
           "   }" +
           "}");
    inspectionFixture.get().checkQuickFix("Remove 'default' branch",
                  """
                    class X {  void x(E e) {    switch (e) {
                        case A, B:
                            int x;
                            x = 2;        System.out.println(x);      }   }}""");
  }

  @Test
  public void testCaseDefaultInEnumSwitch() {
    inspectionFixture.get().doTest("class X {" +
           "  void x(E e) {" +
           "    switch (e) {" +
           "      case A, B:" +
           "        break;" +
           "      /*'default' branch is unnecessary*/default/*_*//**/:" +
           "        break;" +
           "    }" +
           "  }" +
           "}");
    inspectionFixture.get().checkQuickFix("Remove 'default' branch",
                  "class X {" +
                  "  void x(E e) {" +
                  "    switch (e) {" +
                  "      case A, B:" +
                  "        break;\n" +
                  "}" +
                  "  }" +
                  "}");
  }

  @Test
  public void testCaseDefaultInSealedSwitch() {
    inspectionFixture.get().doTest("class X {" +
           "  void x(I i) {" +
           "    switch (i) {" +
           "      case I ii when Math.random() > 0.5:" +
           "        break;" +
           "      case C1 c1:" +
           "        break;" +
           "      case C2 c1:" +
           "        break;" +
           "      /*'default' branch is unnecessary*/default/*_*//**/:" +
           "        break;" +
           "    }" +
           "  }" +
           "}");
    inspectionFixture.get().checkQuickFix("Remove 'default' branch",
                  "class X {" +
                  "  void x(I i) {" +
                  "    switch (i) {" +
                  "      case I ii when Math.random() > 0.5:" +
                  "        break;" +
                  "      case C1 c1:" +
                  "        break;" +
                  "      case C2 c1:" +
                  "        break;\n" +
                  "}" +
                  "  }" +
                  "}");
  }

  @Test
  public void testDefaultInParameterizedSealedHierarchy() {
    inspectionFixture.get().doTest("class X {" +
           "  void x(J<Integer> j) {" +
           "    switch (j) {" +
           "      case D2 d2 -> {}" +
           "      /*'default' branch is unnecessary*/default/*_*//**/ -> {}" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testDefaultWithDominance() {
    inspectionFixture.get().doTest("""
    import org.jetbrains.annotations.Nullable;
    class TestExample {
        sealed interface II {
            record AI() implements II {}
    
            record BI() implements II {}
        }
    
        record RI(@Nullable II value) {}
    
        private static II getII(RI ri) {
            return switch (ri) {
                case RI(II.BI bi) -> bi;
                case RI(II.AI ai) -> ai;
                default ->  null;
            };
        }
    }
    """);
  }

  @Test
  public void testNullDefaultWithDominance() {
    inspectionFixture.get().doTest("""
    import org.jetbrains.annotations.Nullable;
    class TestExample {
        sealed interface II {
            record AI() implements II {}
    
            record BI() implements II {}
        }
    
        record RI(@Nullable II value) {}
    
        private static II getII(RI ri) {
            return switch (ri) {
                case RI(II.BI bi) -> bi;
                case RI(II.AI ai) -> ai;
                case null, default ->  null;
            };
        }
    }
    """);
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
enum E { A, B }
sealed interface I {}
final class C1 implements I {}
final class C2 implements I {}
sealed interface J<T> {}
final class D1 implements J<String> {}
final class D2<T> implements J<T> {}
"""
    };
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final UnnecessaryDefaultInspection inspection = new UnnecessaryDefaultInspection();
    inspection.onlyReportSwitchExpressions = false;
    return inspection;
  }
}