// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

/**
 * @author Bas Leijdekkers
 */
public class UnwrapSwitchExpressionTest extends UnwrapTestCase {

  public void testSimple() {
    assertUnwrapped("boolean b = switch(0) {\n" +
                    "            case 1 -> <caret>false;\n" +
                    "            default -> true;\n" +
                    "        };",
                    "boolean b = false;");
  }
}
