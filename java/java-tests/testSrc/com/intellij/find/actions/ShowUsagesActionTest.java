// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class ShowUsagesActionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
  }

  public void testMultipleUsagesInOneInjectedLine() {
    doTest(true);
  }

  public void testMultipleUsagesInMultipleInjectedLines() {
    doTest(false);
  }

  public void testMultipleUsagesInOneLine() {
    doTest(true);
  }

  public void testMultipleUsagesInMultipleLines() {
    doTest(false);
  }

  private void doTest(boolean isOneLine) {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement variable = getFile().findElementAt(offset).getParent();
    Collection<UsageInfo> usageInfos = myFixture.findUsages(variable);
    List<UsageInfo2UsageAdapter> usages = ContainerUtil.map(usageInfos, UsageInfo2UsageAdapter::new);
    myFixture.openFileInEditor(file.getVirtualFile());
    assertEquals(isOneLine, ShowUsagesAction.areAllUsagesInOneLine(ContainerUtil.getFirstItem(usages), usages));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/find/showUsages/";
  }


}
