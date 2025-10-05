// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.java.codeInspection.MigrateToJavaLangIoInspectionTest;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplicitArrayToStringInspectionTest extends LightJavaInspectionTestCase {

  public void testImplicitArrayToString() {
    doTest();
  }

  public void testImplicitArrayToStringIO() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_25, () -> {
      MigrateToJavaLangIoInspectionTest.addIOClass(myFixture);
      doTest();
    });
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ImplicitArrayToStringInspection();
  }
}