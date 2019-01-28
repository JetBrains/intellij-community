// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiMethod
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import groovy.transform.CompileStatic

/**
 * @author peter
 */
@CompileStatic
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
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable())
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

  void testNewAnonymousInMethodArgTemplate() {
    configureByTestName()
    myFixture.type('new ')
    myFixture.complete(CompletionType.SMART)
    myFixture.type('\n')
    checkResult()
  }

  void testCollectStatisticsOnMethods() {
    ((StatisticsManagerImpl)StatisticsManager.instance).enableStatistics(myFixture.testRootDisposable)
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'test1', 'test2'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
    myFixture.type('\n2\n')
    checkResult()
    myFixture.type(';\ntes')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'test2', 'test1'
  }

}
