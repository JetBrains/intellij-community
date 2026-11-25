// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.inspections.GuavaInspectionTest;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@Ignore("Manual only")
@RunWith(Suite.class)
@Suite.SuiteClasses({
  ChangeTypeSignatureTest.class,
  GuavaInspectionTest.class,
})
public class AllTypeMigrationTestSuite {
}