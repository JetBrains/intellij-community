// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapOptionsTest extends UnwrapTestCase {
  public void testNoOptions() {
    assertOptions("<caret>\n");
  }
}
