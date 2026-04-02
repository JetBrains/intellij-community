// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionItemLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateModCompletionItemProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor.setShowTemplatesInTests;

abstract public class PostfixTemplateTestCase extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/postfix/templates/" + getSuffix();
  }

  @NotNull
  protected abstract String getSuffix();

  protected boolean useModCommandTemplates() {
    return false;
  }

  protected String textCall() {
    return "\t";
  }

  protected void doTest() {
    if (!useModCommandTemplates()) {
      // Only enable template testing when the test types into template fields (not just Tab)
      // Without it, templates auto-advance past fields with defaults (needed for Cast)
      // and stay on always-stop fields (needed for Field)
      if (!"\t".equals(textCall())) {
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      }
      myFixture.configureByFile(getTestName(true) + ".java");
      myFixture.type(textCall());
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
    else {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      Registry.get("postfix.template.mod.completion.enabled").setValue(true, getTestRootDisposable());
      myFixture.configureByFile(getTestName(true) + ".java");
      LookupElement[] elements = myFixture.completeBasic();
      String substring = myFixture.getFile().getFileDocument().getText()
        .substring(0, myFixture.getCaretOffset());
      List<LookupElement> list = Arrays.stream(elements).filter(t -> {
                                                                  return t instanceof CompletionItemLookupElement completionItemLookupElement &&
                                                                         completionItemLookupElement.item() instanceof PostfixTemplateModCompletionItemProvider.PostfixModCompletionItem modCompletionItem &&
                                                                         substring.endsWith(((PostfixTemplate)modCompletionItem.contextObject()).getKey());
                                                                }
        )
        .toList();
      if (list.size() == 1) {
        myFixture.getLookup().setCurrentItem(list.getFirst());
        myFixture.type(textCall());
      }
      else if (list.isEmpty()) {
        LookupManager.getInstance(getProject()).hideActiveLookup();
        myFixture.type(textCall());
      }
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      if (state != null) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> state.gotoEnd(false));
      }
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  protected void doTestCompletion(@Nullable String text) {
    setShowTemplatesInTests(true, getTestRootDisposable());
    myFixture.configureByFile(getTestName(true) + ".java");
    LookupElement[] elements = myFixture.completeBasic();

    if (!useModCommandTemplates()) {
      if (text != null) {
        for (LookupElement element : elements) {
          LookupElementPresentation presentation = new LookupElementPresentation();
          element.renderElement(presentation);
          if (presentation.getItemText().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)) ||
              presentation.getTypeText().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
            myFixture.getLookup().setCurrentItem(element);
            break;
          }
        }
      }
      myFixture.type(textCall());
    }
    else {
      if (text != null) {
        List<LookupElement> list =
          Arrays.stream(elements).filter(t -> t instanceof CompletionItemLookupElement completionItemLookupElement &&
                                              completionItemLookupElement.item() instanceof PostfixTemplateModCompletionItemProvider.PostfixModCompletionItem)
            .toList();
        for (LookupElement element : list) {
          LookupElementPresentation presentation = new LookupElementPresentation();
          element.renderElement(presentation);
          if (presentation.getItemText().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)) ||
              presentation.getTypeText().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
            myFixture.getLookup().setCurrentItem(element);
            break;
          }
        }
      }
      myFixture.type(textCall());
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    TemplateState state2 = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
    if (state2 != null) {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> state2.gotoEnd(false));
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }
}
