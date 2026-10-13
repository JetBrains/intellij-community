// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicFileParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicFileParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/files", configurator);
  }

  @Test
  public void testEmptyFile() { doParserTest(""); }

  @Test
  public void testPackage() { doParserTest("package a.b.c;"); }
  @Test
  public void testAnnotatedPackage() { doParserTest("@Anno package a.b.c;"); }
  @Test
  public void testUnclosedPackage0() { doParserTest("package"); }
  @Test
  public void testUnclosedPackage1() { doParserTest("package a."); }

  @Test
  public void testImport0() { doParserTest("import java.util.*;"); }
  @Test
  public void testImport1() { doParserTest("import java.util.Arrays;"); }
  @Test
  public void testStaticImport0() { doParserTest("import static java.util.Arrays.*;"); }
  @Test
  public void testStaticImport1() { doParserTest("import static java.util.Arrays.sort;"); }
  @Test
  public void testUnclosedImport0() { doParserTest("import"); }
  @Test
  public void testUnclosedImport1() { doParserTest("import java.awt.*"); }
  @Test
  public void testUnclosedImport2() { doParserTest("import java.awt."); }
  @Test
  public void testUnclosedImport3() { doParserTest("import static a"); }
  @Test
  public void testImportBrokenFromBeginning() { doParserTest("xx import a;"); }

  @Test
  public void testExtraSemicolons() { doParserTest("package p;;\nimport a;;\nclass C{};"); }
  @Test
  public void testFileWithClass() { doParserTest("package a;\nimport b;\npublic class C { }\nclass D { }"); }

  @Test
  public void testBindBefore0() { doParserTest("class A{\n  // comment\n  int field;\n}"); }
  @Test
  public void testBindBefore1() { doParserTest("class A{\n  // comment\n\n  int field;\n}"); }
  @Test
  public void testBindBefore2() { doParserTest("class A{ // comment\n  int field;\n}"); }
  @Test
  public void testBindBefore3() { doParserTest("class A{// comment\n  int field;\n}"); }
  @Test
  public void testBindBefore4() { doParserTest("class A{\n  // comment 1\n  // comment 2\n  // comment 3\n  int field;\n}"); }

  @Test
  public void testBindDocComment0() { doParserTest("/** class comment */\nclass A { }"); }
  @Test
  public void testBindDocComment1() { doParserTest("/** file comment */\npackage a;\nclass A { }"); }
  @Test
  public void testBindDocComment2() { doParserTest("/** file comment */\nimport a;\nclass A { }"); }
  @Test
  public void testBindDocComment3() { doParserTest("class A {\n /** field comment */\n int f;\n}"); }
  @Test
  public void testBindDocComment4() { doParserTest("class A {\n /** field comment */\n// field comment\n int f;\n}"); }

  @Test
  public void testDocCommentSnippet() { doParserTest("\nclass C {\n void foo() {\n///comment\n }\n}\n"); }

  protected abstract void doParserTest(String text);
}