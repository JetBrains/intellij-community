// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.util;

import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GrStringUtilTest {
  @Test
  public void testUnescape() {
    assertEquals("$\\l", GrStringUtil.unescapeString("\\$\\l"));
  }
}
