/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.editor.AbstractLineWrapPositionStrategyTest;
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
  public void testNoWrapOnVarArgs() throws Exception {
    String document = "void method(String p1, String p2, Boolean b1, <WRAP>String...a<EDGE>rgs) {}";
    doTest(myLineWrapStrategy, document, false);
  }
}
