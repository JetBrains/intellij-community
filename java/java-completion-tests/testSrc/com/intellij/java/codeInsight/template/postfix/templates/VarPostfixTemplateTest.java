// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ignatov
 */
public class VarPostfixTemplateTest extends PostfixTemplateTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "var";
  }

  public void testSimple() {
    doTest();
  }

  public void testAdd() {
    doTest();
  }

  public void testStreamStep() {
    if (useModCommandTemplates()) return;
    UiInterceptors.register(new ChooserInterceptor(List.of("Create variable inside current lambda", "Extract as 'map' operation"),
                                                   "Create variable inside current lambda"));
    doTest();
  }

  public void testStreamStep2() {
    if (useModCommandTemplates()) return;
    UiInterceptors.register(new ChooserInterceptor(List.of("Create variable inside current lambda", "Extract as 'map' operation"),
                                                   "Extract as 'map' operation"));
    doTest();
  }

  public void testAnonymous() {
    doTest();
  }

  public static class ModVarPostfixTemplateTest extends VarPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }

    @NeedsIndex.SmartMode(reason = "Requires resolving")
    @Override
    public void testStreamStep() {
      doTest();
    }
  }
}
