// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReference;
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
    ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider").getPoint().registerExtension(new MyVerySlowQuickFixProvider(), getTestRootDisposable());
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
    DaemonCodeAnalyzerEx.getInstanceEx(getProject()).restart(getTestName(false));
    errors = highlightErrors();
    CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(getFile(), getEditor());
    UIUtil.dispatchAllInvocationEvents();
    assertSize(N, errors);
    assertNotEmpty(regFixCalled);
  }

  public void testUnresolvedReferenceQuickFixProviderMustWorkForAnnotatorsToo() {
    ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider").getPoint().registerExtension(new MyVerySlowQuickFixProvider(), getTestRootDisposable());
    @Language("JAVA")
    String text = """
      package x;
      class MyClass {
        public MyClass var1;
      }
      """;
    ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES = false;
    regFixCalled.clear();
    configureFromFileText("X.java", text);

    ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES = true;
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{
      new MyClassAnnotator()}, ()->{
      getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf("MyClass var1"));
      DaemonCodeAnalyzerEx.getInstanceEx(getProject()).restart(getTestName(false));
      List<HighlightInfo> errors = highlightErrors();
      assertOneElement(errors);
      CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(getFile(), getEditor());
      UIUtil.dispatchAllInvocationEvents();
      assertNotEmpty(regFixCalled);
    });
  }

  public static class MyClassAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiReference && element.getText().equals("MyClass")) {
        AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "my class");
        UnresolvedReferenceQuickFixUpdater.getInstance(element.getProject()).registerQuickFixesLater((PsiReference)element, builder);
        builder.create();
        this.iDidIt();
      }
    }
  }
}
