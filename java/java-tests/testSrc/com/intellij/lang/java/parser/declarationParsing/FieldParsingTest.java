/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.java.parser.declarationParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;

public class FieldParsingTest extends JavaParsingTestCase {
  public FieldParsingTest() {
    super("parser-full/declarationParsing/field");
  }

  public void testSimple() { doTest(true); }
  public void testMulti() { doTest(true); }

  public void testUnclosedBracket() { doTest(true); }
  public void testMissingInitializer() { doTest(true); }
  public void testUnclosedComma() { doTest(true); }
  public void testUnclosedSemicolon() { doTest(true); }
  public void testMissingInitializerExpression() { doTest(true); }

  public void testMultiLineUnclosed0() { doTest(true); }
  public void testMultiLineUnclosed1() { doTest(true); }

  public void testComplexInitializer() { doTest(true); }

  public void testErrors() { doTest(true); }
}