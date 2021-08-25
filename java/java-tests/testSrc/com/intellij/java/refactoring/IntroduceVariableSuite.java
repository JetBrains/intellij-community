// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *  @author dsl
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  IntroduceVariableTest.class,
  IntroduceVariableMultifileTest.class,
  InplaceIntroduceVariableTest.class
})
public class IntroduceVariableSuite {
}
