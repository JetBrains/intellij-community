// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle.javadoc;

import com.intellij.psi.impl.source.codeStyle.javadoc.JDParser.FenceInfo;
import com.intellij.testFramework.UsefulTestCase;

import static com.intellij.psi.impl.source.codeStyle.javadoc.JDParser.findCodeFence;

public class JDParserTest extends UsefulTestCase {

  /// Test examples from commonmark spec 0.31.2
  public void testFindCodeFenceCommonmarkSpec() {
    // Example 119: Basic backtick fence
    assertEquals(new FenceInfo('`', 3), findCodeFence("```", true));
    assertEquals(new FenceInfo('`', 3), findCodeFence("```", false));
    assertEquals(new FenceInfo('`', 4), findCodeFence("````", true));
    assertEquals(new FenceInfo('`', 4), findCodeFence("````", false));
    assertEquals(new FenceInfo('`', 6), findCodeFence("``````", true));
    assertEquals(new FenceInfo('`', 6), findCodeFence("``````", false));

    // Example 120: Basic tilde fence
    assertEquals(new FenceInfo('~', 3), findCodeFence("~~~", true));
    assertEquals(new FenceInfo('~', 3), findCodeFence("~~~", false));
    assertEquals(new FenceInfo('~', 4), findCodeFence("~~~~", true));
    assertEquals(new FenceInfo('~', 4), findCodeFence("~~~~", false));
    assertEquals(new FenceInfo('~', 6), findCodeFence("~~~~~~", true));
    assertEquals(new FenceInfo('~', 6), findCodeFence("~~~~~~", false));

    // Example 121: Fewer than three backticks (not a fence)
    assertNull(findCodeFence("``", true));
    assertNull(findCodeFence("``", false));
    assertNull(findCodeFence("~", true));
    assertNull(findCodeFence("~", false));
    assertNull(findCodeFence("`", true));
    assertNull(findCodeFence("`", false));
    assertNull(findCodeFence("", true));
    assertNull(findCodeFence("", false));

    // Example 128: Line starting with other characters before the fence
    assertNull(findCodeFence("> ```", true));
    assertNull(findCodeFence("> ```", false));

    // Example 131: Fence with 1 space indentation
    assertEquals(new FenceInfo('`', 3), findCodeFence(" ```", true));
    assertEquals(new FenceInfo('`', 3), findCodeFence(" ```", false));

    // Example 132: Fence with 2 spaces indentation
    assertEquals(new FenceInfo('`', 3), findCodeFence("  ```", true));
    assertEquals(new FenceInfo('`', 3), findCodeFence("  ```", false));

    // Example 133: Fence with 3 spaces indentation
    assertEquals(new FenceInfo('`', 3), findCodeFence("   ```", true));
    assertEquals(new FenceInfo('`', 3), findCodeFence("   ```", false));

    // Example 134: Four spaces of indentation
    assertNull(findCodeFence("    ```", true));
    assertNull(findCodeFence("    ```", false));
    assertNull(findCodeFence("    ~~~", true));
    assertNull(findCodeFence("    ~~~", false));

    // Example 138
    assertNull(findCodeFence("``` ```", true));
    assertNull(findCodeFence("``` ```", false));

    // Example 142: Backtick fence with info string
    assertEquals(new FenceInfo('`', 3), findCodeFence("```ruby", true));
    assertNull(findCodeFence("```ruby", false));

    // Example 143: Tilde fence with info string and spaces
    assertEquals(new FenceInfo('~', 4), findCodeFence("~~~~    ruby startline=3 $%@#$", true));
    assertNull(findCodeFence("~~~~    ruby startline=3 $%@#$", false));

    // Example 144: Backtick fence with ";" info string
    assertEquals(new FenceInfo('`', 4), findCodeFence("````;", true));
    assertNull(findCodeFence("````;", false));

    // Example 145: Info strings for backtick code blocks cannot contain backticks:
    assertNull(findCodeFence("``` aa ```", true));
    assertNull(findCodeFence("``` aa ```", false));

    // Example 146: Info strings for tilde code blocks can contain backticks and tildes:
    assertEquals(new FenceInfo('~', 3), findCodeFence("~~~ aa ``` ~~~", true));
    assertNull(findCodeFence("~~~ aa ``` ~~~", false));

    // Example 147: Closing code fences cannot have info strings
    assertNull(findCodeFence("``` aaa", false));
    assertEquals(new FenceInfo('`', 3), findCodeFence("``` ", true));
    assertEquals(new FenceInfo('`', 3), findCodeFence("```\t", true));
    assertEquals(new FenceInfo('`', 3), findCodeFence("``` \t  ", true));
  }
}
