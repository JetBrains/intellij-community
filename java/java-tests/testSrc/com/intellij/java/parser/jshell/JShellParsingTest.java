// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.jshell;

import com.intellij.ide.highlighter.JShellFileType;
import com.intellij.java.frontback.psi.impl.syntax.JShellSyntaxDefinitionExtension;
import com.intellij.java.frontback.psi.impl.syntax.JavaSyntaxDefinitionExtension;
import com.intellij.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.lang.java.JShellParserDefinition;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.syntax.JShellElementTypeConverterExtension;
import com.intellij.lang.java.syntax.JavaElementTypeConverterExtension;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import com.intellij.platform.syntax.psi.LanguageSyntaxDefinitions;

public class JShellParsingTest extends JavaParsingTestCase {

  public JShellParsingTest() {
    super("parser-full/jshell", JShellFileType.DEFAULT_EXTENSION, new JShellParserDefinition(), new JavaParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addExplicitExtension(ElementTypeConverters.getInstance(), JShellLanguage.INSTANCE, new JavaElementTypeConverterExtension());
    addExplicitExtension(ElementTypeConverters.getInstance(), JShellLanguage.INSTANCE, new JShellElementTypeConverterExtension());
    addExplicitExtension(LanguageSyntaxDefinitions.getINSTANCE(), JShellLanguage.INSTANCE, new JShellSyntaxDefinitionExtension());
  }

  public void testClass0() { doTest(); }
  public void testStatement0() { doTest(); }
  public void testStatement1() { doTest(); }
  public void testExpression0() { doTest(); }
  public void testExpression1() { doTest(); }
  public void testField0() { doTest(); }
  public void testMethod0() { doTest(); }
  public void testImport0() { doTest(); }
  public void testVarStatement() { doTest(); }
  public void testGenericDeclaration1() { doTest(); }
  public void testGenericDeclaration2() { doTest(); }

  private void doTest() {
    doTest(true, true);
  }
}
