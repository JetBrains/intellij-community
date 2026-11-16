// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.testFramework.ModPlanRenameTestSupport.withEveryRenamer

/**
 * The tests of the legacy rename of fields, run on the ModPlan engine. A test that fails here is a difference of the ModPlan rename from the
 * legacy one.
 */
class RenameFieldOnModPlanTest : RenameFieldTest() {
  override fun rename(element: PsiElement, newName: String) {
    renameOnModPlanInTests(project, element, newName, withEveryRenamer(ModPlanOptionValues.EMPTY))
  }

  /**
   * A usage in the Java snippet of a Markdown comment is in an injected fragment. The legacy rename edits the injected document and
   * reformats the host. Here the plan fails an assertion of the postponed formatting of the fragment copy, or it writes another text.
   */
  override fun testRenameInSnippet() {
    try {
      super.testRenameInSnippet()
    }
    catch (_: AssertionError) {
      return
    }
    fail("The known gap is gone: remove the override")
  }
}
