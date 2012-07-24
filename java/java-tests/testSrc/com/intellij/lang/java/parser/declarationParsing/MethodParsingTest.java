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

public class MethodParsingTest extends JavaParsingTestCase {
  public MethodParsingTest() {
    super("parser-full/declarationParsing/method");
  }

  public void testNormal1() { doTest(true); }
  public void testNormal2() { doTest(true); }

  public void testUnclosed1() { doTest(true); }
  public void testUnclosed2() { doTest(true); }
  public void testUnclosed3() { doTest(true); }
  public void testUnclosed4() { doTest(true); }
  public void testUnclosed5() { doTest(true); }
  public void testUnclosed6() { doTest(true); }
  public void testGenericMethod() { doTest(true); }
  public void testGenericMethodErrors() { doTest(true); }
  public void testErrors0() { doTest(true); }
  public void testErrors1() { doTest(true); }
  public void testErrors2() { doTest(true); }
  public void testErrors3() { doTest(true); }
  public void testCompletionHack() { doTest(true); }
  public void testCompletionHack1() { doTest(true); }

  public void testNoLocalMethod() { doTest(true); }

  public void testWildcardParsing() { doTest(true); }
}