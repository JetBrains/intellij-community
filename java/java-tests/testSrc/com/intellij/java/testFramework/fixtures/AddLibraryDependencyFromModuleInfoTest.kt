// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.testFramework.fixtures

import com.intellij.codeInsight.daemon.QuickFixBundle
import org.junit.Assert

class AddLibraryDependencyFromModuleInfoTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  fun testAddDependencyNoLibrariesFound() {
    val file = myFixture.addFileToProject("../src_m2/module-info.java", "module M2 {requires lib.na<caret>med;}")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    Assert.assertNull(myFixture.getAvailableIntention (QuickFixBundle.message("orderEntry.fix.add.library.to.classpath", "lib.named")))
  }
}