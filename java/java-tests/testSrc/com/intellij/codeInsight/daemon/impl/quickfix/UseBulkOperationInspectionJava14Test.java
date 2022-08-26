// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class UseBulkOperationInspectionJava14Test extends UseBulkOperationInspectionTest {
  @Override
  protected String getBasePath() {
    return super.getBasePath()+"/java14";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_4;
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk14();
  }
}
