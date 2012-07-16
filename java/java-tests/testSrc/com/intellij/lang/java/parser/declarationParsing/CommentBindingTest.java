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

public class CommentBindingTest extends JavaParsingTestCase {
  public CommentBindingTest() {
    super("parser-full/declarationParsing/commentBinding");
  }

  public void testBindBefore1() { doTest(true); }
  public void testBindBefore2() { doTest(true); }
  public void testBindBefore3() { doTest(true); }
  public void testBindBefore3a() { doTest(true); }
  public void testBindBefore4() { doTest(true); }
  public void testBindBefore5() { doTest(true); }

  public void testBindBeforeClass1() { doTest(true); }
  public void testBindBeforeClass2() { doTest(true); }
  public void testBindBeforeClass3() { doTest(true); }
  public void testBindBeforeClass4() { doTest(true); }
  public void testBindBeforeClass5() { doTest(true); }
  public void testBindBeforeClass6() { doTest(true); }
}