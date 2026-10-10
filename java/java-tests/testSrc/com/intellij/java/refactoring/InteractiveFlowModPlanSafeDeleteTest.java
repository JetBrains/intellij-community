// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow;
import com.intellij.refactoring.safeDelete.ModPlanJavaSafeDeleteOptions;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The tests of the interactive safe delete, run with the flow on the ModPlan engine. A test that fails here is a difference of the
 * ModPlan flow from the legacy one.
 * <p>
 * The answers are the ones of the legacy unit-test mode: all the unused callees are deleted, and the parameter is deleted in all the
 * callers that only pass it on.
 */
public class InteractiveFlowModPlanSafeDeleteTest extends SafeDeleteTest {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("refactoring.safeDelete.modPlan").setValue(true, getTestRootDisposable());
    ModPlanSafeDeleteFlow.INSTANCE.failOnLegacyInTests(getTestRootDisposable());
  }

  @Override
  protected @NotNull Map<ModPlanOption<?>, Object> defaultAnswers() {
    return legacyUnitTestAnswers();
  }

  @Override
  protected boolean runsOnModPlan() {
    return true;
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

  /**
   * @return the answers of the legacy unit-test mode, which the ModPlan flows do not give by themselves
   */
  static @NotNull Map<ModPlanOption<?>, Object> legacyUnitTestAnswers() {
    return Map.of(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, true, ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, true);
  }
}
