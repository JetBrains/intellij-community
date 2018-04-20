// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionLookupArranger;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author yole
 */
public class CompletionServiceTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/";
  }

  public void testCompletionServiceSimple() {
    myFixture.configureByFile("Simple.java");
    CompletionServiceImpl service = (CompletionServiceImpl)CompletionService.getCompletionService();
    Disposable completionDisposable = Disposer.newDisposable();
    try {
      CompletionParameters parameters = service.createCompletionParameters(getProject(),
                                                                           myFixture.getEditor(),
                                                                           myFixture.getEditor().getCaretModel().getPrimaryCaret(),
                                                                           1,
                                                                           CompletionType.BASIC,
                                                                           completionDisposable);

      CompletionLookupArranger arranger = service.createLookupArranger(parameters);
      service.performCompletion(parameters, result -> arranger.addElement(result));
      Pair<List<LookupElement>, Integer> items = arranger.arrangeItems();
      LookupElement element = ContainerUtil.find(items.first, item -> item.getLookupString().equals("_field"));
      WriteCommandAction.runWriteCommandAction(getProject(), () -> service.handleCompletionItemSelected(parameters, element, arranger.itemMatcher(element), '\n'));
      myFixture.checkResultByFile("Simple_afterCompletionService.java");
    }
    finally {
      Disposer.dispose(completionDisposable);
    }
  }
}
