// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.classlayout;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.classlayout.FinalPrivateMethodInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class FinalPrivateMethodFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new FinalPrivateMethodInspection()));

  @Test
  public void testSimple() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 """
                   private final/**/ boolean isPositive(int number) {
                       return number > 0;
                   }""",
                 """
                   private boolean isPositive(int number) {
                       return number > 0;
                   }"""
    );
  }

  @Test
  public void testDoNotFixOnPackageMethod() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                               """
                                 class X {
                                   final boolean isPositive(int number) {
                                     return number > 0;
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotFixOnProtectedMethod() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                               """
                                 class X {
                                   protected final boolean isPositive(int number) {
                                     return number > 0;
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotFixOnPublicMethod() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                               """
                                 class X {
                                   public final boolean isPositive(int number) {
                                     return number > 0;
                                   }
                                 }
                                 """);
  }
}
