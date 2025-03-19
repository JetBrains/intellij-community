// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ConfusingMainMethodInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new ConfusingMainMethodInspection();
  }

  public void testConfusingMainMethod() {
    doTest();
  }

  public void testConfusingMainMethodInImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      doTest();
    });
  }
}