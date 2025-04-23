// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LazyQuickFixTest extends LightQuickFixTestCase {
  private static final List<Throwable> regFixCalled = Collections.synchronizedList(new ArrayList<>());
  private static volatile boolean ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES;
  private static class MyCountingQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
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
    ((LazyQuickFixUpdaterImpl)LazyQuickFixUpdater.getInstance(getProject())).stopUntil(resolveInBackground);
    ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider").getPoint().registerExtension(new MyCountingQuickFixProvider(), getTestRootDisposable());
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
    CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(getFile(), getEditor());
    UIUtil.dispatchAllInvocationEvents();
    assertSize(N, errors);
    assertNotEmpty(regFixCalled);
  }

  public void testLazyQuickFixesMustWorkForAnnotatorsToo() {
    @Language("JAVA")
    String text = """
      package x;
      class MyClass2 {
        public MyClass var1;
      }
      """;
    configureFromFileText("X.java", text);

    MyLazyFixAnnotator.invoked = false;
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{
      new MyLazyFixAnnotator()}, ()-> {
      getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf("MyClass var1"));
      DaemonCodeAnalyzerEx.getInstanceEx(getProject()).restart(getTestName(false));
      List<HighlightInfo> errors = highlightErrors();
      assertTrue(ContainerUtil.exists(errors, h->"my class".equals(h.getDescription())));
      CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(getFile(), getEditor());

      IntentionAction fix = findActionWithText("my lazy fix");
      assertNotNull(fix);
      invoke(fix);

      assertTrue(MyLazyFixAnnotator.invoked);
    });
  }

  // highlight "MyClass"
  public static class MyLazyFixAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    static boolean invoked;
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiReference && element.getText().equals("MyClass")) {
        holder.newAnnotation(HighlightSeverity.ERROR, "my class")
            .withLazyQuickFix(registrar -> {
              registrar.register(new AbstractIntentionAction() {
                @Override
                public @NotNull String getText() {
                  return "my lazy fix";
                }

                @Override
                public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
                  invoked = true;
                }
              });
            }).create();
        this.iDidIt();
      }
    }
  }

  public void testLazyQuickFixDoesNotGetComputedEagerlyIfItsFarAwayFromTheCaretAndDoesGetComputedIfTheUnresolvedReferenceIsCloseToTheCaret() {
    Disposable resolveInBackground = Disposer.newDisposable();
    ((LazyQuickFixUpdaterImpl)LazyQuickFixUpdater.getInstance(getProject())).stopUntil(resolveInBackground);
    ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider").getPoint().registerExtension(new MyCountingQuickFixProvider(), getTestRootDisposable());
    regFixCalled.clear();
    @Language("JAVA")
    String text = "class AClass {{ " +
                  "fooooo();\n"+
                  "\n".repeat(1000) +
                  "<caret>" +
                  "\n".repeat(1000) +
                  "  }}\n";
    ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES = false;
    configureFromFileText("x.java", text);
    EditorTestUtil.setEditorVisibleSizeInPixels(getEditor(), 1000, 1000);
    getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    ProperTextRange visibleRange = getEditor().calculateVisibleRange();
    assertTrue(visibleRange.toString(), visibleRange.getStartOffset() > 1000);
    List<HighlightInfo> infos = highlightErrors();
    CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(getFile(), getEditor());
    assertTrue(String.valueOf(infos), ContainerUtil.exists(infos, h-> "Cannot resolve method 'fooooo' in 'AClass'".equals(h.getDescription())));
    assertSize(0, regFixCalled); // must not compute

    ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES = true;
    getEditor().getCaretModel().moveToOffset(0);
    getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    visibleRange = getEditor().calculateVisibleRange();
    assertEquals(visibleRange.toString(), 0, visibleRange.getStartOffset());
    type("x");
    backspace();  // change psi to revalidate cached values
    infos = highlightErrors();
    assertTrue(String.valueOf(infos), ContainerUtil.exists(infos, h-> "Cannot resolve method 'fooooo' in 'AClass'".equals(h.getDescription())));
    CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(getFile(), getEditor());
    assertSize(1, regFixCalled); // now must compute, since it's close to the caret
    Disposer.dispose(resolveInBackground);
  }

  private static class MyLazyFixHighlightVisitor implements HighlightVisitor {
    private static volatile boolean infoCreated;
    private static volatile boolean fixComputed;
    private HighlightInfoHolder myHolder;

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
      return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
      if (element instanceof PsiComment) {
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element).description("MY: XXX")
            .registerLazyFixes(registrar -> {
              fixComputed = true;
            }).create());
        infoCreated = true;
      }
    }

    static boolean isMy(HighlightInfo info) {
      return HighlightSeverity.WARNING.equals(info.getSeverity()) && "MY: XXX".equals(info.getDescription());
    }

    @Override
    public boolean analyze(@NotNull PsiFile file,
                           boolean updateWholeFile,
                           @NotNull HighlightInfoHolder holder,
                           @NotNull Runnable action) {
      myHolder = holder;
      action.run();
      return true;
    }

    @Override
    public @NotNull HighlightVisitor clone() {
      return new MyLazyFixHighlightVisitor();
    }
  }

  public void testLazyQuickFixMustNotRecomputeItsExpensiveComputationOnEveryDaemonRestart() {
    MyLazyFixHighlightVisitor visitor = new MyLazyFixHighlightVisitor();
    getProject().getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, getTestRootDisposable());
    @Language("JAVA")
    String text = """
      class X {
        void f(boolean b) {
          if (b) {
            // xxx<caret>
          }
        }
      }
      """;
    configureFromFileText("x.java", text);

    MyLazyFixHighlightVisitor.infoCreated = false;
    MyLazyFixHighlightVisitor.fixComputed = false;

    List<HighlightInfo> myWarns = ContainerUtil.filter(doHighlighting(), h -> MyLazyFixHighlightVisitor.isMy(h));
    assertOneElement(myWarns);
    assertTrue(MyLazyFixHighlightVisitor.infoCreated);
    assertTrue(MyLazyFixHighlightVisitor.fixComputed);

    MyLazyFixHighlightVisitor.infoCreated = false;
    MyLazyFixHighlightVisitor.fixComputed = false;
    DaemonCodeAnalyzerImpl.getInstance(getProject()).restart();
    doHighlighting();
    assertTrue(MyLazyFixHighlightVisitor.infoCreated);
    assertFalse(MyLazyFixHighlightVisitor.fixComputed); // must not recompute on each restart

    WriteCommandAction.writeCommandAction(getProject()).run(() -> getEditor().getDocument().setText(""));
    doHighlighting();
    WriteCommandAction.writeCommandAction(getProject()).run(() -> getEditor().getDocument().setText(text));
    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf("<caret>"));
    MyLazyFixHighlightVisitor.infoCreated = false;
    MyLazyFixHighlightVisitor.fixComputed = false;
    doHighlighting();
    assertTrue(MyLazyFixHighlightVisitor.infoCreated);
    assertTrue(MyLazyFixHighlightVisitor.fixComputed); // when text changed too much, it must recompute
  }
}
