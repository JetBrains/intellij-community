// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class SwitchPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "switch";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_7);
  }

  public void testInt() {
    doTest();
  }

  public void testByte() {
    doTest();
  }

  public void testChar() {
    doTest();
  }

  public void testShort() {
    doTest();
  }

  public void testEnum() {
    doTest();
  }

  public void testString() {
    doTest();
  }

  public void testComposite() {
    doTest();
  }

  public static class ModSwitchPostfixTemplateTest extends SwitchPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}