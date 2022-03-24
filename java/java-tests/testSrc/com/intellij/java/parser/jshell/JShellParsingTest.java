// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.jshell;

import com.intellij.ide.highlighter.JShellFileType;
import com.intellij.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.JShellParserDefinition;
import com.intellij.lang.java.JavaParserDefinition;

public class JShellParsingTest extends JavaParsingTestCase {

  public JShellParsingTest() {
    super("parser-full/jshell", JShellFileType.DEFAULT_EXTENSION, new JShellParserDefinition(), new JavaParserDefinition());
  }

  public void testClass0() { doTest(true); }
  public void testStatement0() { doTest(true); }
  public void testStatement1() { doTest(true); }
  public void testExpression0() { doTest(true); }
  public void testExpression1() { doTest(true); }
  public void testField0() { doTest(true); }
  public void testMethod0() { doTest(true); }
  public void testImport0() { doTest(true); }
}
