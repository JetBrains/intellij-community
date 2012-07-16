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
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;

public class ForParsingTest extends JavaParsingTestCase {
  public ForParsingTest() {
    super("parser-full/statementParsing/for");
  }

  public void testNormal1() { doTest(true); }
  public void testNormal2() { doTest(true); }
  public void testForEach1() { doTest(true); }

  public void testUncomplete1() { doTest(true); }
  public void testUncomplete2() { doTest(true); }
  public void testUncomplete3() { doTest(true); }
  public void testUncomplete4() { doTest(true); }
  public void testUncomplete5() { doTest(true); }
  public void testUncomplete6() { doTest(true); }
  public void testUncomplete7() { doTest(true); }
  public void testUncomplete8() { doTest(true); }
  public void testUncomplete9() { doTest(true); }
  public void testUncomplete10() { doTest(true); }
}