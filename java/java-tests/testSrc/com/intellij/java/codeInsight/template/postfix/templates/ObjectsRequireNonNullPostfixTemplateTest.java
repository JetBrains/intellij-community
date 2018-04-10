// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class ObjectsRequireNonNullPostfixTemplateTest extends PostfixTemplateTestCase {
  private LanguageLevel myDefaultLanguageLevel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension levelProjectExtension = LanguageLevelProjectExtension.getInstance(myFixture.getProject());
    myDefaultLanguageLevel = levelProjectExtension.getLanguageLevel();
    levelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      LanguageLevelProjectExtension.getInstance(myFixture.getProject()).setLanguageLevel(myDefaultLanguageLevel);
      myDefaultLanguageLevel = null;
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  public void testSimple() {
    doTest();
  }

  public void testPrimitive() {
    doTest();
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "reqnonnull";
  }
}
