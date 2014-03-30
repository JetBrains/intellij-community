/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
/**
 * User: anna
 */
public class Normal8CompletionTest extends LightFixtureCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/lambda/completion/normal/";
  }

  public void testSelfStaticsOnly() throws Exception {
    configureByFile("SelfStaticsOnly.java");
    assertStringItems("ba", "bar");
  }

  public void testFinishWithColon() {
    myFixture.configureByText "a.java", """
class Foo {{ Object o = Fo<caret>x }}
"""
    myFixture.completeBasic()
    myFixture.type('::')
    myFixture.checkResult """
class Foo {{ Object o = Foo::<caret>x }}
"""
  }

  public void testNoSuggestionsAfterMethodReferenceAndDot() {
    String text = """
class Foo {{ Object o = StringBuilder::append.<caret> }}
"""
    myFixture.configureByText "a.java", text
    assertEmpty(myFixture.completeBasic())
    myFixture.checkResult(text)
  }
}
