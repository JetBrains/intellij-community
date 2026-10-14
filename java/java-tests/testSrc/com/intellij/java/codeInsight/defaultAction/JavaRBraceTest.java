// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.defaultAction;

import com.intellij.codeInsight.AbstractBasicJavaRBraceTest;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

@TestApplication
public class JavaRBraceTest extends AbstractBasicJavaRBraceTest {
  @Test
  public void testClosingBraceReformatsBlock() { doTest(); } //requires formatting
  @Test
  public void test1() { doTest(); } //requires formatting
}
