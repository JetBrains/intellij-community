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

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

abstract public class PostfixTemplateTestCase extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/postfix/templates/" + getSuffix();
  }

  @NotNull
  protected abstract String getSuffix();

  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }
}
