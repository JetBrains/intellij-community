// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.apiUse;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public final class Java16With17APIUsageInspectionTest extends BaseApiUsageTestCase {

  public void testLanguageLevel16() {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/api_usage/use17api_on_16";
  }

  @Override
  protected @NotNull Sdk getSdk() {
    return JAVA_17.getSdk();
  }

  @Override
  protected @NotNull LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_16;
  }

}
