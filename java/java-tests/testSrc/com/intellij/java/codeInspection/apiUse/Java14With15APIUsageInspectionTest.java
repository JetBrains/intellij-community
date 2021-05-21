// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.apiUse;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public final class Java14With15APIUsageInspectionTest extends BaseApiUsageTestCase {

  public void testLanguageLevel14() {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/api_usage/use15api_on_14";
  }

  @Override
  protected @NotNull Sdk getSdk() {
    return JAVA_15.getSdk();
  }


  @Override
  protected @NotNull LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_14;
  }
}
