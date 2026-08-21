// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;

/**
 * @author Bas Leijdekkers
 */
public final class OverrideImplementLanguageLevelTest extends OverrideImplementBaseTest {
  @Override
  protected String getBaseDir() {
    return "/codeInsight/overrideImplement/";
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk21(); 
  }

  public void testImplementListJava8() {
    // List.of() available, but not allowed by language level.
    doTest(false, Boolean.TRUE);
  }
}
