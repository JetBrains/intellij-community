// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_18;

public class JavadocSnippetInjectionFileTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) {
    final int offset = getEditor().getCaretModel().getPrimaryCaret().getOffset();
    final PsiElement snippet = PsiUtilCore.getElementAtOffset(getFile(), offset);

    final PsiClass enclosingClass = getEnclosingClass(snippet);
    final PsiClass injectedClass = getInjectedClass(snippet);
    WriteAction.run(() -> enclosingClass.replace(injectedClass));

    checkResult(testName);
  }

  private void checkResult(@NotNull final String testName) {
    final String expectedFilePath = getBasePath() + "/after" + testName;
    checkResultByFile(expectedFilePath);
  }

  private static @NotNull PsiClass getEnclosingClass(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiClass.class);
  }

  private @NotNull PsiClass getInjectedClass(PsiElement element) {
    final PsiSnippetDocTag snippet = PsiTreeUtil.getParentOfType(element, PsiSnippetDocTag.class);
    final AtomicReference<PsiElement> injected = new AtomicReference<>();
    final InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(getProject());
    injectionManager.enumerate(snippet, (injectedPsi, places) -> { injected.set(injectedPsi); });

    return PsiTreeUtil.findChildOfType(injected.get(), PsiClass.class);
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/javadoc/snippet/file";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_18;
  }
}
