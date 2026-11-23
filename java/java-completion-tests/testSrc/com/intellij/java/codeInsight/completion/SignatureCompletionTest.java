package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
@IndexingModeTemplate
public class SignatureCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/signature/");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    Registry.get("java.completion.argument.live.template").setValue(true);
    Registry.get("java.completion.show.constructors").setValue(true);
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
  }

  @AfterEach
  void tearDown() {
    Registry.get("java.completion.argument.live.template").setValue(false);
    Registry.get("java.completion.show.constructors").setValue(false);
  }

  private void checkResult() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  private void doFirstItemTest() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.type("\n");
      checkResult();
    });
  }

  @Test
  public void testOnlyDefaultConstructor() { doFirstItemTest(); }

  @Test
  public void testNonDefaultConstructor() { doFirstItemTest(); }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testAnonymousNonDefaultConstructor() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.type("\n");
      checkResult();
      myFixture.type("\n");
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_afterTemplate.java");
    });
  }

  @Test
  public void testSeveralConstructors() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.SMART);
      List<LookupElement> items = myFixture.getLookup().getItems();
      assertEquals(3, items.size());
      assertEquals(0, ((PsiMethod)items.get(0).getObject()).getParameterList().getParametersCount());
      myFixture.type("\n");
      checkResult();
    });
  }

  @Test
  public void testNewAnonymousInMethodArgTemplate() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.type("new ");
      myFixture.complete(CompletionType.SMART);
      myFixture.type("\n");
      checkResult();
    });
  }

  @Test
  public void testCollectStatisticsOnMethods() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "test1", "test2");
      myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
      myFixture.type("\n2\n");
      checkResult();
      myFixture.type(";\ntes");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "test2", "test1");
    });
  }
}
