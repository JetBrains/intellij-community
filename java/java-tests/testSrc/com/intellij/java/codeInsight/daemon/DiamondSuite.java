// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.java.codeInsight.daemon.impl.quickfix.ChangeNewOperatorTypeTest;
import com.intellij.java.codeInsight.daemon.impl.quickfix.Simplify2DiamondInspectionsTest;
import com.intellij.java.refactoring.IntroduceParameterTest;
import com.intellij.java.refactoring.IntroduceVariableTest;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@Ignore("Manual only")
@RunWith(Suite.class)
@Suite.SuiteClasses({
  LightAdvHighlightingJdk7Test.class,
  Simplify2DiamondInspectionsTest.class,
  IntroduceParameterTest.class,
  IntroduceVariableTest.class,
  ChangeNewOperatorTypeTest.class,
})
public class DiamondSuite {
}
