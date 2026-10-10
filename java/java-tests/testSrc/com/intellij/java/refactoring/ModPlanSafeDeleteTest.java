// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.testFramework.SafeDeleteTestSupport;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * The tests of the interactive safe delete, run through the headless safe delete on the ModPlan engine, as the MCP tool runs it. A test
 * that fails here is a difference of the headless safe delete from the legacy one.
 * <p>
 * The answers are the ones of the legacy unit-test mode, as in {@link InteractiveFlowModPlanSafeDeleteTest}.
 */
public class ModPlanSafeDeleteTest extends SafeDeleteTest {
  @Override
  protected @NotNull Map<ModPlanOption<?>, Object> defaultAnswers() {
    return InteractiveFlowModPlanSafeDeleteTest.legacyUnitTestAnswers();
  }

  @Override
  protected boolean runsOnModPlan() {
    return true;
  }

  @Override
  protected void performSafeDelete(@NotNull List<PsiElement> elements,
                                   @NotNull Map<ModPlanOption<?>, Object> answers,
                                   @NotNull SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsageRecorder) {
    SafeDeleteTestSupport.headlessSafeDelete(getProject(), elements, SafeDeleteTestSupport.withAll(ModPlanOptionValues.EMPTY, answers),
                                             BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore(), nonCodeUsageRecorder);
  }

  /** A usage in generated code is a conflict for the user to confirm, where the legacy flow deletes without a question. */
  @Override
  public void testUsageInGenerated() {
    try {
      super.testUsageInGenerated();
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of method <b><code>A.foo()</code></b> in generated code that is not safe to delete.", e.getMessage());
    }
  }
}
