// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.structuralsearch;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.structuralsearch.plugin.ui.DetectFileTypeTestCase;

/**
 * @author Bas Leijdekkers
 */
public class JavaCommunityDetectFileTypeTest extends DetectFileTypeTestCase {
  public void testDetectJava() {
    doTest(JavaFileType.INSTANCE, "class X {{  System.out.println<caret>();}}");
  }
}