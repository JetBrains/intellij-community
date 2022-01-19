// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_17;

public class JavadocSnippetInjectionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(getTestRootDisposable());
  }

  @Override
  protected void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) {
    final Language language = getInjectedLanguage();
    final Language expectedLang = Language.findLanguageByID(actionHint.getExpectedText());

    assertThat(language)
      .withFailMessage(String.format("Language '%s' should be injected, but found '%s'", actionHint.getExpectedText(), language.getID()))
      .isEqualTo(expectedLang);

  }

  @NotNull
  private Language getInjectedLanguage() {
    int offset = getEditor().getCaretModel().getPrimaryCaret().getOffset();
    final PsiSnippetDocTag snippet = (PsiSnippetDocTag) PsiUtilCore.getElementAtOffset(getFile(), offset).getParent();
    final AtomicReference<PsiElement> injected = new AtomicReference<>();
    final InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(getProject());
    injectionManager.enumerate(snippet, (injectedPsi, places) -> { injected.set(injectedPsi); });

    return injected.get().getLanguage();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/javadoc/snippet";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }
}
