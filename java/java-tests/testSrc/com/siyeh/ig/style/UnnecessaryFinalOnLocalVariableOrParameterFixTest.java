// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class UnnecessaryFinalOnLocalVariableOrParameterFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryFinalOnLocalVariableOrParameterInspection()));

  @Test
  public void testRemoveFinalOnParameter() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
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

  @Test
  public void testRemoveFinalOnLocalVariable() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
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

  @Test
  public void testRemoveFinalOnParameterUsedInLambda() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
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

  @Test
  public void testRemoveFinalOnLocalVariableUsedInLambda() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
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
