/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class CastPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "cast";
  }

  public void testSingleExpression() {
    doTest();
  } // jdk mock needed

  public void testVoidExpression() {
    doTest();
  }

  public void testSingleArgument() {
    doTest();
  }

  public void testInsideString() {
    doTest();
  }

  public void testChainCall() {
    doTest();
  }
  public void testTernary() {
    doTest();
  }
}
