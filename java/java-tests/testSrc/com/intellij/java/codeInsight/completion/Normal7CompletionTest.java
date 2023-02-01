// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class Normal7CompletionTest extends NormalCompletionTestCase {
  public void testGenericInsideDiamond() { doTest(); }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }
}
