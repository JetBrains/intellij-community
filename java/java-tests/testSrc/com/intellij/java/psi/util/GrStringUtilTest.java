// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.util;

import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.junit.Assert;

public class GrStringUtilTest extends UsefulTestCase {
  public void testUnescape() {
    Assert.assertEquals("$\\l", GrStringUtil.unescapeString("\\$\\l"));
  }
}
