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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;

/**
 * @author Denis Zhdanov
 * @since 09/21/2010
 */
public class StaticTextWhiteSpaceDefinitionStrategyTest {

  private StaticTextWhiteSpaceDefinitionStrategy myStrategy;  

  @Before
  public void setUp() {
    myStrategy = new StaticTextWhiteSpaceDefinitionStrategy("abc");
  }

  @Test
  public void notAtFirstPosition() {
    assertSame(0, myStrategy.check(" abc", 0, 4));
    assertSame(1, myStrategy.check("  abc", 1, 3));
  }

  @Test
  public void match() {
    assertSame(3, myStrategy.check("abc", 0, 3));
    assertSame(4, myStrategy.check(" abcde", 1, 5));
  }

  @Test
  public void withoutEnd() {
    assertSame(0, myStrategy.check("abc", 0, 2));
    assertSame(1, myStrategy.check(" abc", 1, 3));
  }
}
