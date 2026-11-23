// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.util.registry.Registry

/** The tests of the inline rename of Java members, with the rename on the ModPlan engine. */
class RenameMembersInplaceOnModPlanTest : RenameMembersInplaceTest() {
  override fun setUp() {
    super.setUp()
    Registry.get("refactoring.rename.modPlan").setValue(true, testRootDisposable)
  }
}
