package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.NeedsIndex;

import java.util.List;

public class SignatureCompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/signature/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("java.completion.argument.live.template").setValue(true);
    Registry.get("java.completion.show.constructors").setValue(true);
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Registry.get("java.completion.argument.live.template").setValue(false);
      Registry.get("java.completion.show.constructors").setValue(false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void checkResult() {
    checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doFirstItemTest() {
    configureByTestName();
    myFixture.type("\n");
    checkResult();
  }

  public void testOnlyDefaultConstructor() { doFirstItemTest(); }

  public void testNonDefaultConstructor() { doFirstItemTest(); }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testAnonymousNonDefaultConstructor() {
    configureByTestName();
    myFixture.type("\n");
    checkResult();
    myFixture.type("\n");
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile(getTestName(false) + "_afterTemplate.java");
  }

  public void testSeveralConstructors() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.SMART);
    List<LookupElement> items = myFixture.getLookup().getItems();
    assertEquals(3, items.size());
    assertEquals(0, ((PsiMethod)items.get(0).getObject()).getParameterList().getParametersCount());
    myFixture.type("\n");
    checkResult();
  }

  public void testNewAnonymousInMethodArgTemplate() {
    configureByTestName();
    myFixture.type("new ");
    myFixture.complete(CompletionType.SMART);
    myFixture.type("\n");
    checkResult();
  }

  public void testCollectStatisticsOnMethods() {
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "test1", "test2");
    myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
    myFixture.type("\n2\n");
    checkResult();
    myFixture.type(";\ntes");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "test2", "test1");
  }
}
