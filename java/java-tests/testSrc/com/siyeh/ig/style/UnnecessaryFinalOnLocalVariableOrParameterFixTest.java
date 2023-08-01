// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryFinalOnLocalVariableOrParameterFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryFinalOnLocalVariableOrParameterInspection();
  }

  public void testRemoveFinalOnParameter() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 """
                   public int foo(final/**/ int number) {
                     return number + 10;
                   }
                   """,
                 """
                   public int foo(int number) {
                     return number + 10;
                   }
                   """
    );
  }

  public void testRemoveFinalOnLocalVariable() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 """
                   public int foo(int number) {
                     final/**/ int localNumber = number * 2;
                     return localNumber + 10;
                   }
                   """,
                 """
                   public int foo(int number) {
                     int localNumber = number * 2;
                     return localNumber + 10;
                   }
                   """
    );
  }

  public void testRemoveFinalOnParameterUsedInLambda() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 """
                   public java.util.function.Function foo(final/**/ int number) {
                     return (int i) -> number + i;
                   }
                   """,
                 """
                   public java.util.function.Function foo(int number) {
                     return (int i) -> number + i;
                   }
                   """
    );
  }

  public void testRemoveFinalOnLocalVariableUsedInLambda() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 """
                   public java.util.function.Function foo(int number) {
                     final/**/ int localNumber = number * 2;
                     return (int i) -> localNumber + i;
                   }
                   """,
                 """
                   public java.util.function.Function foo(int number) {
                     int localNumber = number * 2;
                     return (int i) -> localNumber + i;
                   }
                   """
    );
  }
}
