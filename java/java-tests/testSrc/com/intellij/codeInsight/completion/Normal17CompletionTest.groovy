/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull

class Normal17CompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/"
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7
  }

  void testOnlyExceptionsInMultiCatch1() { doTest() }

  void testOnlyExceptionsInMultiCatch2() { doTest() }

  void testOnlyResourcesInResourceList1() { doTest() }

  void testOnlyResourcesInResourceList2() { doTest() }

  void testOnlyResourcesInResourceList3() { doTest() }

  void testOnlyResourcesInResourceList4() { doTest() }

  void testOnlyResourcesInResourceList5() { doTest() }

  void testMethodReferenceNoStatic() { doTest() }

  void testMethodReferenceCallContext() { doTest() }

  void testResourceParentInResourceList() {
    configureByTestName()
    assert 'MyOuterResource' == myFixture.lookupElementStrings[0]
    assert 'MyClass' in myFixture.lookupElementStrings
    myFixture.type('C\n')
    checkResultByFile(getTestName(false) + "_after.java")
  }

  private void doTest() {
    configureByTestName()
    myFixture.type('\n')
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void testAfterTryWithResources() {
    configureByTestName()
    def strings = myFixture.lookupElementStrings
    assert strings.containsAll(['final', 'finally', 'int', 'Util'])
  }

  void testNewObjectHashMapWithSmartEnter() {
    configureByTestName()
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT)
    checkResultByFile(getTestName(false) + "_after.java")
  }
}
