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

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class StreamPostfixTemplateTest extends PostfixTemplateTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "stream";
  }

  public void testSimple() {
    doTest();
  }

  public void testExpressionContext() {
    doTest();
  }
  
  public void testInLambda() {
    if (DumbService.isDumb(myFixture.getProject()) &&
        !Registry.is("ide.dumb.mode.check.awareness")) {
      // See IDEA-362230
      return;
    }
    doTest();
  }

  public void testAssignment() {
    doTest();
  }

  public void testNotAvailable() {
    doTest();
  }

  public void testDoNotExpandOnJavaLess8() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_6, this::doTest);
  }
}

