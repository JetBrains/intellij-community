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

public class TryPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "try";
  }

  public void testSimple() {
    doTest();
  }

  public void testMultiStatement() {
    doTest();
  }

  public void testNotStatement() {
    doTest();
  }

  public void testNotResolvedExpression() {
    doTest();
  }

  public void testDeclarationStatement() {
    doTest();
  }

  public void testExpressionInMethodBody() {
    doTest();
  }

  public void testSimpleWithThrowsCheckedException() {
    doTest();
  }

  public void testIncompleteStatement() {
    doTest();
  }

  public void testConstructorStatement() {
    doTest();
  }
}
