// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.jshell;

import com.intellij.ide.highlighter.JShellFileType;
import com.intellij.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.JShellParserDefinition;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

@TestApplication
public class JShellParsingTest extends JavaParsingTestCase {

  public JShellParsingTest() {
    super("parser-full/jshell", JShellFileType.DEFAULT_EXTENSION, new JShellParserDefinition(), new JavaParserDefinition());
  }

  @Test
  public void testClass0() { doTest(); }
  @Test
  public void testStatement0() { doTest(); }
  @Test
  public void testStatement1() { doTest(); }
  @Test
  public void testExpression0() { doTest(); }
  @Test
  public void testExpression1() { doTest(); }
  @Test
  public void testField0() { doTest(); }
  @Test
  public void testMethod0() { doTest(); }
  @Test
  public void testImport0() { doTest(); }
  @Test
  public void testVarStatement() { doTest(); }
  @Test
  public void testGenericDeclaration1() { doTest(); }
  @Test
  public void testGenericDeclaration2() { doTest(); }

  private void doTest() {
    doTest(true, true);
  }
}
