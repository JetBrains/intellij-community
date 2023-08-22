// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class RedundantMethodOverrideInspectionTest extends LightJavaInspectionTestCase {

  private final RedundantMethodOverrideInspection myInspection = new RedundantMethodOverrideInspection();

  @Override
  protected InspectionProfileEntry getInspection() {
    myInspection.checkLibraryMethods = true;
    myInspection.ignoreDelegates = false;
    return myInspection;
  }

  public void testRedundantMethodOverride() { doTest(); }

  public void testMutualRecursion() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    doTest();
  }

  public void testLibraryOverride() {
    myFixture.allowTreeAccessForAllFiles();
    doTest();
  }

  public void testSwitch() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, this::doTest);
  }

  public void testInstanceOf() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, this::doTest);
  }

  public void testForEachPatterns() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_20_PREVIEW, this::doTest);
  }

  public void testIgnoreDelegates(){
    boolean ignoreDelegatesOldValue = myInspection.ignoreDelegates;
    try {
      myInspection.ignoreDelegates = true;
      doTest();
    }
    finally {
      myInspection.ignoreDelegates = ignoreDelegatesOldValue;
    }
  }
}
