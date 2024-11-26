// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class AsListToListPostfixTemplateTest extends PostfixTemplateTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "asList";
  }

  public void testSimple() {
    doTestCompletion("asList");
  }

}

