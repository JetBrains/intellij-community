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
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiMethod
/**
 * @author peter
 */
class SignatureCompletionTest extends LightFixtureCompletionTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/signature/"
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    Registry.get("java.completion.argument.live.template").value = true
    Registry.get("java.completion.show.constructors").value = true
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable())
  }

  @Override
  protected void tearDown() throws Exception {
    Registry.get("java.completion.argument.live.template").value = false
    Registry.get("java.completion.show.constructors").value = false
    super.tearDown()
  }

  private checkResult() {
    checkResultByFile(getTestName(false) + "_after.java")
  }

  private void doFirstItemTest() {
    configureByTestName()
    myFixture.type('\n')
    checkResult()
  }

  void testOnlyDefaultConstructor() { doFirstItemTest() }

  void testNonDefaultConstructor() { doFirstItemTest() }

  void testAnonymousNonDefaultConstructor() {
    configureByTestName()
    myFixture.type('\n')
    checkResult()
    myFixture.type('\n')
    checkResultByFile(getTestName(false) + "_afterTemplate.java")
  }

  void testSeveralConstructors() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.complete(CompletionType.SMART)
    def items = myFixture.lookup.items
    assert items.size() == 3
    assert ((PsiMethod) items[0].object).parameterList.parametersCount == 0
    myFixture.type('\n')
    checkResult()
  }

}
