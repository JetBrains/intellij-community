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
import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class NotExpressionPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "not";
  }

  public void testSimple() {
    doTest();
  }

  public void testComplexCondition() {
    doTest();
  }

  public void testBoxedBoolean() {
    doTest();
  }

  public void testExclamation() {
    doTest();
  }

  public void testConditionInBooleanMethodCall() {
    doTest();
  }

  public void testSmartNegationPresentEmpty() {
    doTest();
  }

  public void testSmartNegationNoneAny() {
    doTest();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package java.util;\n" +
                       "public class Optional<T> {\n" +
                       "  public static <T> Optional<T> of(T value) { return null; }\n" +
                       "  public boolean isPresent() { return true; }\n" +
                       "  public boolean isEmpty() { return false;  }\n" +
                       "}");
    myFixture.addClass("package java.util.stream;\n" +
                       "\n" +
                       "import java.util.function.Predicate;\n" +
                       "\n" +
                       "public class Stream<T> {\n" +
                       "  boolean anyMatch(Predicate<? super T> predicate) { return true;}\n" +
                       "  boolean noneMatch(Predicate<? super T> predicate) { return false;}\n" +
                       "}");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }

  //  public void testNegation()          { doTest(); } // todo: test for chooser
}