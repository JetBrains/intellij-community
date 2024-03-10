// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class UnnecessarilyQualifiedStaticallyImportedElementInspectionTest extends IGInspectionTestCase {

  public void testSimple() { doTest(); }
  public void testSameMemberNames() { doTest(); }
  public void testMethodRef() { doTest(); }
  public void testChainedMethodCall() { doTest(); }
  public void testOverriding() { doTest(); }
  public void testYield() { doTest(); }
  public void testInterfacing() { doTest(); }

  private void doTest() {
    doTest("com/siyeh/igtest/style/unnecessarily_qualified_statically_imported_element/" + getTestName(true),
           new UnnecessarilyQualifiedStaticallyImportedElementInspection());
  }
}