/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java.parser.partial;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.FileParser;
import com.intellij.lang.java.parser.JavaParsingTestCase;


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

  public void testFileWithClass() { doParserTest("package a;\n" +
                                                 "import b;\n" +
                                                 "public class C { }\n" +
                                                 "class D { }"); }

  private void doParserTest(final String text) {
    doParserTest(text, new Parser() {
      public void parse(final PsiBuilder builder) {
        FileParser.parse(builder);
      }
    });
  }
}
