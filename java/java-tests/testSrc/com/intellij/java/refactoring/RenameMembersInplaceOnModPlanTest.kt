// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication

/** The tests of the inline rename of Java members, with the rename on the ModPlan engine. */
@TestApplication
@RegistryKey(key = "refactoring.rename.modPlan", value = "true")
class RenameMembersInplaceOnModPlanTest : RenameMembersInplaceTest()
