// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

/**
 * @author Bas Leijdekkers
 */
public final class UnwrapTypeParameterTest extends UnwrapTestCase {

  public void testSimple() {
    assertUnwrapped("java.util.Map<<caret>Integer, String> x;",
                    "Integer x;");
  }
  
  public void testNested() {
    assertUnwrapped("java.util.List<java.util.List<java.util.List<<caret>String>>> x;",
                    "java.util.List<java.util.List<String>> x;");
  }
}
