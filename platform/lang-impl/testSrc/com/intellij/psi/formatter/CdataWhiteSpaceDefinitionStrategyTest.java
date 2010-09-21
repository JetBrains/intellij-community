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
package com.intellij.psi.formatter;

import static org.junit.Assert.*;
import com.intellij.psi.formatter.CdataWhiteSpaceDefinitionStrategy;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;

/**
 * @author Denis Zhdanov
 * @since 09/20/2010
 */
public class CdataWhiteSpaceDefinitionStrategyTest {

  private CdataWhiteSpaceDefinitionStrategy myStrategy;  

  @Before
  public void setUp() {
    myStrategy = new CdataWhiteSpaceDefinitionStrategy();
  }

  @Test
  public void withoutClosingSection() {
    String text = "<![CDATA[xxx]>"; // Doesn't contain double ']'
    assertSame(0, myStrategy.check(text, 0, text.length()));

    text = "<![CDATA[xxx]]>";
    assertSame(0, myStrategy.check(text, 0, text.length() - 1)); // Last symbol is out of scope
  }

  @Test
  public void cdataNotAtFirstOffset() {
    String text = "  <![CDATA[xxx]]>";
    assertSame(0, myStrategy.check(text, 0, text.length()));
    assertSame(1, myStrategy.check(text, 1, text.length()));
  }

  @Test
  public void partialMatch() {
    String text = "<![CDATA[xxx]]>   ";
    int expectedOffset = text.indexOf(">") + 1;
    assertSame(expectedOffset, myStrategy.check(text, 0, text.length()));
    assertSame(expectedOffset + 1, myStrategy.check(" " + text, 1, text.length()));
  }
}
