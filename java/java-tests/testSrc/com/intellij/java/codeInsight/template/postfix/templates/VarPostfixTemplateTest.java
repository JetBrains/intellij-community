/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.testFramework.LightProjectDescriptor;
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
    UiInterceptors.register(new ChooserInterceptor(List.of("Create variable inside current lambda", "Extract as 'map' operation"), 
                                                   "Create variable inside current lambda"));
    doTest();
  }

  public void testStreamStep2() {
    UiInterceptors.register(new ChooserInterceptor(List.of("Create variable inside current lambda", "Extract as 'map' operation"), 
                                                   "Extract as 'map' operation"));
    doTest();
  }

  public void testAnonymous() {
    doTest();
  }
}
