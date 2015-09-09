/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull

public class Normal17CompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/"
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST
  }

  public void testOnlyExceptionsInMultiCatch1() { doTest() }
  public void testOnlyExceptionsInMultiCatch2() { doTest() }

  public void testOnlyResourcesInResourceList1() { doTest() }
  public void testOnlyResourcesInResourceList2() { doTest() }
  public void testOnlyResourcesInResourceList3() { doTest() }
  public void testOnlyResourcesInResourceList4() { doTest() }
  public void testOnlyResourcesInResourceList5() { doTest() }

  public void testMethodReferenceNoStatic() { doTest() }
  public void testMethodReferenceCallContext() { doTest() }

  public void testResourceParentInResourceList() {
    configureByFile(getTestName(false) + ".java")
    assert 'MyOuterResource' == myFixture.lookupElementStrings[0]
    assert 'MyClass' in myFixture.lookupElementStrings
    myFixture.type('C\n')
    checkResultByFile(getTestName(false) + "_after.java")
  }

  private void doTest() {
    configureByFile(getTestName(false) + ".java")
    myFixture.type('\n')
    checkResultByFile(getTestName(false) + "_after.java")
  }

  public void testAfterTryWithResources() {
    configureByFile(getTestName(false) + ".java")
    def strings = myFixture.lookupElementStrings
    assert strings.containsAll(['final', 'finally', 'int', 'Util'])
  }
}
