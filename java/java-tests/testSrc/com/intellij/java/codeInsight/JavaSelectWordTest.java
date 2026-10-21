// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.AbstractBasicJavaSelectWordTest;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

@TestApplication
public class JavaSelectWordTest extends AbstractBasicJavaSelectWordTest {
  @Test
  public void testWithFolding() { doTest("java", true, true); }
}
