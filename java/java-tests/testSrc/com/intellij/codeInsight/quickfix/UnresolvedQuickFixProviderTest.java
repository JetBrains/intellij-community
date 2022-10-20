// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.quickfix;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UnresolvedReferenceQuickFixUpdaterImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UnresolvedQuickFixProviderTest extends LightDaemonAnalyzerTestCase {
  private static final List<Throwable> regFixCalled = Collections.synchronizedList(new ArrayList<>());
  private static volatile boolean ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES;
  private static class MyVerySlowQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
    @Override
    public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
      regFixCalled.add(new Throwable());
      //TimeoutUtil.sleep(1000);
      if (!ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES) {
        fail("Must not register unresolved reference fixes synchronously");
      }
    }
    @NotNull
    @Override
    public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
      return PsiJavaCodeReferenceElement.class;
    }
  }

  public void testUnresolvedReferenceQuickFixProviderMustRegisterItsQuickFixesLazily() {
    Disposable resolveInBackground = Disposer.newDisposable();
    ((UnresolvedReferenceQuickFixUpdaterImpl)UnresolvedReferenceQuickFixUpdater.getInstance(getProject())).stopUntil(resolveInBackground);
    UnresolvedReferenceQuickFixProvider.EP_NAME.getPoint().registerExtension(new MyVerySlowQuickFixProvider(), getTestRootDisposable());
    int N = 1000;
    String unresolvedDeclarations = IntStream.range(0, N).mapToObj(i-> "public UnknownClassNumber" + i + " var" + i + ";\n").collect(Collectors.joining());
    @Language("JAVA")
    String text = """
      package x;
      class X {
                   
      """ + unresolvedDeclarations + """
                   
      }
      """;
    ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES = false;
    regFixCalled.clear();
    configureFromFileText("X.java", text);
    List<HighlightInfo> errors = highlightErrors();
    assertSize(N, errors);
    assertEmpty(StringUtil.join(regFixCalled, t-> ExceptionUtil.getThrowableText(t), "\n----\n"), regFixCalled);
    Disposer.dispose(resolveInBackground);
    regFixCalled.clear();

    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf("UnknownClassNumber15"));
    ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES = true;
    DaemonCodeAnalyzer.getInstance(getProject()).restart();
    errors = highlightErrors();
    CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(getFile(), getEditor());
    UIUtil.dispatchAllInvocationEvents();
    assertSize(N, errors);
    assertNotEmpty(regFixCalled);
  }
}
