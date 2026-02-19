// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.defaultAction;

import com.intellij.codeInsight.AbstractBasicJavaRBraceTest;

public class JavaRBraceTest extends AbstractBasicJavaRBraceTest {
  public void testClosingBraceReformatsBlock() { doTest(); } //requires formatting
  public void test1() { doTest(); } //requires formatting
}
