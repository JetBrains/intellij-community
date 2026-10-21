// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.defaultAction;

import com.intellij.codeInsight.AbstractBasicJavaLBraceTest;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

@TestApplication
public class JavaLBraceTest extends AbstractBasicJavaLBraceTest {
  @Test
  public void testIndentLBrace() { doTest(); } //requires formatting

}
