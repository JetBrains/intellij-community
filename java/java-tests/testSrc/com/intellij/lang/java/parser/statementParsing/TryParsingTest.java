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

public class TryParsingTest extends JavaParsingTestCase {
  public TryParsingTest() {
    super("parser-full/statementParsing/try");
  }

  public void testNormal1() { doTest(true); }
  public void testNormal2() { doTest(true); }
  public void testNormal3() { doTest(true); }
  public void testNormal4() { doTest(true); }

  public void testIncomplete1() { doTest(true); }
  public void testIncomplete2() { doTest(true); }
  public void testIncomplete3() { doTest(true); }
  public void testIncomplete4() { doTest(true); }
  public void testIncomplete5() { doTest(true); }
  public void testIncomplete6() { doTest(true); }
  public void testIncomplete7() { doTest(true); }
  public void testIncomplete8() { doTest(true); }
  public void testIncomplete9() { doTest(true); }
}