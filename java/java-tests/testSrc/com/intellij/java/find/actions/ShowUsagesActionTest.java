// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.find.actions;

import com.intellij.JavaTestUtil;
import com.intellij.find.actions.ShowUsagesAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ShowUsagesActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/find/showUsages/");
    new MyTestInjector(PsiManager.getInstance(myFixture.getProject())).injectAll(myFixture.getTestRootDisposable());
  }

  @Test
  public void testMultipleUsagesInOneInjectedLine() {
    doTest(true);
  }

  @Test
  public void testMultipleUsagesInMultipleInjectedLines() {
    doTest(false);
  }

  @Test
  public void testMultipleUsagesInOneLine() {
    doTest(true);
  }

  @Test
  public void testMultipleUsagesInMultipleLines() {
    doTest(false);
  }

  private void doTest(boolean isOneLine) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    int offset = ReadAction.computeBlocking(() -> myFixture.getEditor().getCaretModel().getOffset());
    PsiElement variable = ReadAction.computeBlocking(() -> myFixture.getFile().findElementAt(offset).getParent());
    Collection<UsageInfo> usageInfos = ReadAction.computeBlocking(() -> myFixture.findUsages(variable));
    List<UsageInfo2UsageAdapter> usages = ReadAction.computeBlocking(() -> ContainerUtil.map(usageInfos, UsageInfo2UsageAdapter::new));
    EdtTestUtil.runInEdtAndWait(() -> myFixture.openFileInEditor(file.getVirtualFile()));
    ReadAction.runBlocking(() -> assertEquals(isOneLine, ShowUsagesAction.areAllUsagesInOneLine(ContainerUtil.getFirstItem(usages), usages)));
  }

}
