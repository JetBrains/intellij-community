// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
@SuppressWarnings("EnumSwitchStatementWhichMissesCases")
public class EnumSwitchStatementWhichMissesCasesInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new EnumSwitchStatementWhichMissesCasesInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    /*'switch' statement on enum type 'E' misses case 'C'*/switch/**/ (e) {" +
           "      case A:" +
           "      case B:" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testTwoMissing() {
    inspectionFixture.get().doTest("enum E { A, B, C, D }" +
           "class X {" +
           "  void m(E e) {" +
           "    /*'switch' statement on enum type 'E' misses cases 'C' and 'D'*/switch/**/ (e) {" +
           "      case A:" +
           "      case B:" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testManyMissing() {
    inspectionFixture.get().doTest("enum E { FIRST, SECOND, THIRD, FOURTH, FIFTH, SIXTH, SEVENTH, EIGHTH, NINTH }" +
           "class X {" +
           "  void m(E e) {" +
           "    /*'switch' statement on enum type 'E' misses cases 'FIRST', 'SECOND', 'THIRD', 'FOURTH', 'FIFTH', ...*/switch/**/ (e) {" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testFullyCovered() {
    inspectionFixture.get().doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    switch(e) {" +
           "      case A:" +
           "      case B:" +
           "      case C:" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testUnresolved() {
    inspectionFixture.get().doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    switch(e) {" +
           "      case <error descr=\"Cannot resolve symbol 'D'\">D</error>:" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testSyntaxErrorInLabel() {
    inspectionFixture.get().doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    switch(e) {" +
           "      case <error descr=\"Constant expression required\">(A)</error>:" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testDfaFullyCovered() {
    inspectionFixture.get().doTest("""
             enum E {A, B, C}

             class X {
               void m(E e) {
                 if(e == E.C) return;
                 switch ((e)) {
                   case A:
                   case B:
                 }
               }
             }""");
  }

  @Test
  public void testDfaNotCovered() {
    inspectionFixture.get().doTest("""
             enum E {A, B, C}

             class X {
               void m(E e) {
                 if(e == E.C || e == E.B) return;
                 /*'switch' statement on enum type 'E' misses case 'A'*/switch/**/ (e) {
                 }
               }
             }""");
  }

  @Test
  public void testDfaPossibleValues() {
    inspectionFixture.get().doTest("""
             enum E {A, B, C}

             class X {
               void m(E e) {
                 if(e == E.A || e == E.B) {
                   switch (e) {
                     case A:
                     case B:
                   }
                 }
               }
             }""");
  }

  @Test
  public void testDfaPossibleValuesNotCovered() {
    inspectionFixture.get().doTest("""
             enum E {A, B, C}

             class X {
               void m(E e) {
                 if(e == E.A || e == E.B) {
                   /*'switch' statement on enum type 'E' misses case 'B'*/switch/**/ (e) {
                     case A:
                   }
                 }
               }
             }""");
  }
  
  @Test
  public void testDfaJoinEphemeral() {
    inspectionFixture.get().doTest("""
             enum X {A, B, C}

             class Test {
               void test(X x, boolean b, boolean c) {
                 if (b) {
                   if (x == null || x == X.A || x == X.B || x == X.C) return;
                 } else if (c) {
                   if (x == null || x == X.A) return;
                 } else {
                   if (x == null || x == X.B) return;
                 }
                 /*'switch' statement on enum type 'X' misses cases 'A', 'B', and 'C'*/switch/**/ (x) {
                 }
               }
             }""");
  }

  @Test
  public void testJava14() {
    inspectionFixture.get().doTest("""
             enum E {A, B, C}

             class X {
               void m(E e) {
                 switch(e) {
                   case A -> {}
                   case B -> {}
                   case C -> {}
                 }
                 /*'switch' statement on enum type 'E' misses case 'C'*/switch/**/(e) {
                   case A -> {}
                   case B -> {}
                 }
                 /*'switch' statement on enum type 'E' misses case 'C'*/switch/**/(e) {
                   case A, B -> {}
                 }
                 /*'switch' statement on enum type 'E' misses case 'C'*/switch/**/(e) {
                   case A, B:break;
                 }
                \s
               }
             }""");
  }

}