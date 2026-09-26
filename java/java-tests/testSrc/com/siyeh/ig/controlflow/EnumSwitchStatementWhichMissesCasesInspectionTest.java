// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("EnumSwitchStatementWhichMissesCases")
public class EnumSwitchStatementWhichMissesCasesInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    /*'switch' statement on enum type 'E' misses case 'C'*/switch/**/ (e) {" +
           "      case A:" +
           "      case B:" +
           "    }" +
           "  }" +
           "}");
  }

  public void testTwoMissing() {
    doTest("enum E { A, B, C, D }" +
           "class X {" +
           "  void m(E e) {" +
           "    /*'switch' statement on enum type 'E' misses cases 'C' and 'D'*/switch/**/ (e) {" +
           "      case A:" +
           "      case B:" +
           "    }" +
           "  }" +
           "}");
  }

  public void testManyMissing() {
    doTest("enum E { FIRST, SECOND, THIRD, FOURTH, FIFTH, SIXTH, SEVENTH, EIGHTH, NINTH }" +
           "class X {" +
           "  void m(E e) {" +
           "    /*'switch' statement on enum type 'E' misses cases 'FIRST', 'SECOND', 'THIRD', 'FOURTH', 'FIFTH', ...*/switch/**/ (e) {" +
           "    }" +
           "  }" +
           "}");
  }

  public void testFullyCovered() {
    doTest("enum E { A, B, C }" +
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

  public void testUnresolved() {
    doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    switch(e) {" +
           "      case <error descr=\"Cannot resolve symbol 'D'\">D</error>:" +
           "    }" +
           "  }" +
           "}");
  }

  public void testSyntaxErrorInLabel() {
    doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    switch(e) {" +
           "      case <error descr=\"Constant expression required\">(A)</error>:" +
           "    }" +
           "  }" +
           "}");
  }

  public void testDfaFullyCovered() {
    doTest("""
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

  public void testDfaNotCovered() {
    doTest("""
             enum E {A, B, C}

             class X {
               void m(E e) {
                 if(e == E.C || e == E.B) return;
                 /*'switch' statement on enum type 'E' misses case 'A'*/switch/**/ (e) {
                 }
               }
             }""");
  }

  public void testDfaPossibleValues() {
    doTest("""
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

  public void testDfaPossibleValuesNotCovered() {
    doTest("""
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
  
  public void testDfaJoinEphemeral() {
    doTest("""
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

  public void testJava14() {
    doTest("""
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


  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new EnumSwitchStatementWhichMissesCasesInspection();
  }
}