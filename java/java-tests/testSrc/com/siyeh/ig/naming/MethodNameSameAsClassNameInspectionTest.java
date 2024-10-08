// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

public class MethodNameSameAsClassNameInspectionTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDefaultHint = "Make method constructor";
  }

  @Override
  protected BaseInspection getInspection() {
    return new MethodNameSameAsClassNameInspection();
  }

  public void testSimple() { doTest(); }
  public void testModifiers() { doTest(); }
  public void testAbstract() { assertQuickfixNotAvailable(); }
  public void testInterface() { assertQuickfixNotAvailable(); }
  public void testConflictingCtor() { assertQuickfixNotAvailable(); }
  public void testConflictingCtorErasure() { assertQuickfixNotAvailable(); }

  @SuppressWarnings("SpellCheckingInspection")
  public void testmain() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(), () -> assertQuickfixNotAvailable());
  }

  @Override
  protected String getRelativePath() {
    return "naming/method_name_same_as_class_name";
  }
}