/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.parser.JavaParser;

public class FileParserTest extends JavaParsingTestCase {
  public FileParserTest() {
    super("parser-partial/files");
  }

  public void testEmptyFile() { doParserTest(""); }

  public void testPackage() { doParserTest("package a.b.c;"); }
  public void testAnnotatedPackage() { doParserTest("@Anno package a.b.c;"); }
  public void testUnclosedPackage0() { doParserTest("package"); }
  public void testUnclosedPackage1() { doParserTest("package a."); }

  public void testImport0() { doParserTest("import java.util.*;"); }
  public void testImport1() { doParserTest("import java.util.Arrays;"); }
  public void testStaticImport0() { doParserTest("import static java.util.Arrays.*;"); }
  public void testStaticImport1() { doParserTest("import static java.util.Arrays.sort;"); }
  public void testUnclosedImport0() { doParserTest("import"); }
  public void testUnclosedImport1() { doParserTest("import java.awt.*"); }
  public void testUnclosedImport2() { doParserTest("import java.awt."); }
  public void testUnclosedImport3() { doParserTest("import static a"); }
  public void testImportBrokenFromBeginning() { doParserTest("xx import a;"); }

  public void testExtraSemicolons() { doParserTest("package p;;\nimport a;;\nclass C{};"); }
  public void testFileWithClass() { doParserTest("package a;\nimport b;\npublic class C { }\nclass D { }"); }

  public void testBindBefore0() { doParserTest("class A{\n  // comment\n  int field;\n}"); }
  public void testBindBefore1() { doParserTest("class A{\n  // comment\n\n  int field;\n}"); }
  public void testBindBefore2() { doParserTest("class A{ // comment\n  int field;\n}"); }
  public void testBindBefore3() { doParserTest("class A{// comment\n  int field;\n}"); }
  public void testBindBefore4() { doParserTest("class A{\n  // comment 1\n  // comment 2\n  // comment 3\n  int field;\n}"); }

  public void testBindDocComment0() { doParserTest("/** class comment */\nclass A { }"); }
  public void testBindDocComment1() { doParserTest("/** file comment */\npackage a;\nclass A { }"); }
  public void testBindDocComment2() { doParserTest("/** file comment */\nimport a;\nclass A { }"); }
  public void testBindDocComment3() { doParserTest("class A {\n /** field comment */\n int f;\n}"); }
  public void testBindDocComment4() { doParserTest("class A {\n /** field comment */\n// field comment\n int f;\n}"); }

  private void doParserTest(String text) {
    doParserTest(text, builder -> JavaParser.INSTANCE.getFileParser().parse(builder));
  }
}