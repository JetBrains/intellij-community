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
package com.intellij.java.psi.formatter.java;

import com.intellij.openapi.editor.AbstractLineWrapPositionStrategyTest;
import com.intellij.psi.formatter.java.JavaLineWrapPositionStrategy;
import org.junit.Before;
import org.junit.Test;

public class JavaLineWrapTest extends AbstractLineWrapPositionStrategyTest {
  private JavaLineWrapPositionStrategy myLineWrapStrategy;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    myLineWrapStrategy = new JavaLineWrapPositionStrategy();
  }

  @Test
  public void testNoWrapOnVarArgs() {
    String document = "void method(String p1, String p2, Boolean b1, <WRAP>String...a<EDGE>rgs) {}";
    doTest(myLineWrapStrategy, document, false);
  }

  @Test
  public void testNoWrapOnDouble1() {
    String document = "double t = 1000000 + <WRAP>.112<EDGE>122";
    doTest(myLineWrapStrategy, document, false);
  }

  @Test
  public void testNoWrapOnDouble2() {
    String document = "double t = 1000000 + <WRAP>112.<EDGE>";
    doTest(myLineWrapStrategy, document, false);
  }

  @Test
  public void testNoWrapOnFloat2() {
    String document = "float t = 1000000 + <WRAP>11111.112<EDGE>122";
    doTest(myLineWrapStrategy, document, false);
  }
  
}
