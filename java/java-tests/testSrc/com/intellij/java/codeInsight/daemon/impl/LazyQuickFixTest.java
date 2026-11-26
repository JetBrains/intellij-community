// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.LazyQuickFixUpdaterImpl;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
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
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class LazyQuickFixTest {
  private static final List<Throwable> regFixCalled = Collections.synchronizedList(new ArrayList<>());
  private static volatile boolean ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES;
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, List::of, LightDaemonAnalyzerOptions.DEFAULT.withProductionDaemon());

  @BeforeEach
  void setUp() {
    daemonFixture.get().getDaemonCodeAnalyzer().setUpdateByTimerEnabled(true);
  }

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

  @Test
  public void testUnresolvedReferenceQuickFixProviderMustRegisterItsQuickFixesLazily() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() -> {
      LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
      Project project = myFixture.getProject();
      Disposable resolveInBackground = Disposer.newDisposable();
      ((LazyQuickFixUpdaterImpl)LazyQuickFixUpdater.getInstance(project)).stopUntil(resolveInBackground);
      ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider").getPoint().registerExtension(new MyCountingQuickFixProvider(), myFixture.getTestRootDisposable());
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
      daemon.configureFromFileText("X.java", text);
      List<HighlightInfo> errors = daemon.waitHighlighting(HighlightSeverity.ERROR);
      assertEquals(N, errors.size());
      CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(project, myFixture.getEditor());
      assertTrue(regFixCalled.isEmpty(), () -> StringUtil.join(regFixCalled, t-> ExceptionUtil.getThrowableText(t), "\n----\n"));
      Disposer.dispose(resolveInBackground);
      regFixCalled.clear();

      EdtTestUtil.runInEdtAndWait(() -> {
        Editor editor = myFixture.getEditor();
        editor.getCaretModel().moveToOffset(editor.getDocument().getText().indexOf("UnknownClassNumber15"));
      });
      ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES = true;
      daemon.getDaemonCodeAnalyzer().restart(myFixture.getTestName(false));
      errors = daemon.waitHighlighting(HighlightSeverity.ERROR);
      CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(project, myFixture.getEditor());
      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
      assertEquals(N, errors.size());
      assertFalse(regFixCalled.isEmpty());
    });
  }

  @Test
  public void testLazyQuickFixesMustWorkForAnnotatorsToo() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() -> {
      LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
      @Language("JAVA")
      String text = """
        package x;
        class MyClass2 {
          public MyClass var1;
        }
        """;
      daemon.configureFromFileText("X.java", text);

      MyLazyFixAnnotator.invoked = false;
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{
        new MyLazyFixAnnotator()}, ()-> {
        moveCaretToMyClassVar(myFixture);
        daemon.getDaemonCodeAnalyzer().restart(myFixture.getTestName(false));
        List<HighlightInfo> errors = daemon.waitHighlighting(HighlightSeverity.ERROR);
        assertTrue(ContainerUtil.exists(errors, h->"my class".equals(h.getDescription())));
        CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(myFixture.getProject(), myFixture.getEditor());

        IntentionAction fix = ContainerUtil.find(CodeInsightTestFixtureImpl.getAvailableIntentions(myFixture.getEditor(), myFixture.getFile()),
                                                 action -> "my lazy fix".equals(action.getText()));
        assertNotNull(fix);
        daemon.invoke(fix);

        assertTrue(MyLazyFixAnnotator.invoked);
      });
    });
  }

  @Test
  public void testComputedLazyQuickFixCanBeUnregistered() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() -> {
      LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
      @Language("JAVA")
      String text = """
        package x;
        class MyClass2 {
          public MyClass var1;
        }
        """;
      daemon.configureFromFileText("X.java", text);

      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{
        new MyLazyFixAnnotator()}, () -> {
        moveCaretToMyClassVar(myFixture);
        daemon.getDaemonCodeAnalyzer().restart(myFixture.getTestName(false));
        List<HighlightInfo> errors = daemon.waitHighlighting(HighlightSeverity.ERROR);
        HighlightInfo info = ContainerUtil.find(errors, highlight -> "my class".equals(highlight.getDescription()));
        assertNotNull(info);
        CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(myFixture.getProject(), myFixture.getEditor());

        IntentionAction fix = info.findRegisteredQuickFix((descriptor, _) -> descriptor.getAction());
        assertNotNull(fix);
        info.unregisterQuickFix(action -> action == fix);

        assertNull(info.findRegisteredQuickFix((descriptor, _) -> descriptor.getAction() == fix ? descriptor : null));
        assertFalse(info.hasQuickFixes());
      });
    });
  }

  private static void moveCaretToMyClassVar(@NotNull LightJavaCodeInsightTestFixture myFixture) {
    EdtTestUtil.runInEdtAndWait(() -> {
      Editor editor = myFixture.getEditor();
      editor.getCaretModel().moveToOffset(editor.getDocument().getText().indexOf("MyClass var1"));
    });
  }

  @Test
  public void testCompositeIncludesLazyQuickFixesFromAllInfos() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() -> {
      HighlightInfo anchor = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(0, 1).createUnconditionally();
      HighlightInfo infoWithLazyFix = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(0, 1)
        .registerLazyFixes(_ -> { })
        .createUnconditionally();

      HighlightInfo composite = HighlightInfo.createComposite(List.of(anchor, infoWithLazyFix), daemon.getFixture().getProject());

      assertTrue(composite.hasLazyQuickFixes());
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
                public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
                  invoked = true;
                }
              });
            }).create();
        this.iDidIt();
      }
    }
  }

  @Test
  public void testLazyQuickFixDoesNotGetComputedEagerlyIfItsFarAwayFromTheCaretAndDoesGetComputedIfTheUnresolvedReferenceIsCloseToTheCaret() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() -> {
      LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
      Project project = myFixture.getProject();
      Disposable resolveInBackground = Disposer.newDisposable();
      ((LazyQuickFixUpdaterImpl)LazyQuickFixUpdater.getInstance(project)).stopUntil(resolveInBackground);
      ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider").getPoint().registerExtension(new MyCountingQuickFixProvider(), myFixture.getTestRootDisposable());
      regFixCalled.clear();
      @Language("JAVA")
      String text = "class AClass {{ " +
                    "fooooo();\n"+
                    "\n".repeat(1000) +
                    "<caret>" +
                    "\n".repeat(1000) +
                    "  }}\n";
      ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES = false;
      daemon.configureFromFileText("x.java", text);
      ProperTextRange visibleRange = EdtTestUtil.runInEdtAndGet(() -> {
        Editor editor = myFixture.getEditor();
        EditorTestUtil.setEditorVisibleSizeInPixels(editor, 1000, 1000);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        return editor.calculateVisibleRange();
      });
      assertTrue(visibleRange.getStartOffset() > 1000, visibleRange.toString());
      List<HighlightInfo> infos = daemon.waitHighlighting(HighlightSeverity.ERROR);
      CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(project, myFixture.getEditor());
      assertTrue(ContainerUtil.exists(infos, h-> "Cannot resolve method 'fooooo' in 'AClass'".equals(h.getDescription())), String.valueOf(infos));
      assertEquals(0, regFixCalled.size()); // must not compute

      ALLOW_UNRESOLVED_REFERENCE_QUICK_FIXES = true;
      visibleRange = EdtTestUtil.runInEdtAndGet(() -> {
        Editor editor = myFixture.getEditor();
        editor.getCaretModel().moveToOffset(0);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        return editor.calculateVisibleRange();
      });
      assertEquals(0, visibleRange.getStartOffset(), visibleRange.toString());
      daemon.type("x");
      daemon.backspace();  // change psi to revalidate cached values
      infos = daemon.waitHighlighting(HighlightSeverity.ERROR);
      assertTrue(ContainerUtil.exists(infos, h-> "Cannot resolve method 'fooooo' in 'AClass'".equals(h.getDescription())), String.valueOf(infos));
      CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(project, myFixture.getEditor());
      assertEquals(1, regFixCalled.size()); // now must compute, since it's close to the caret
      Disposer.dispose(resolveInBackground);
    });
  }

  // warning for all comments, with lazy quick fix
  private static class MyLazyFixHighlightVisitor implements HighlightVisitor {
    private static volatile boolean infoCreated;
    private static volatile boolean fixComputed;
    private HighlightInfoHolder myHolder;

    @Override
    public boolean suitableForFile(@NotNull PsiFile psiFile) {
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
    public boolean analyze(@NotNull PsiFile psiFile,
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

  @Test
  public void testLazyQuickFixMustNotRecomputeItsExpensiveComputationOnEveryDaemonRestart() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.runTest(() -> {
      LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
      Project project = myFixture.getProject();
      project.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(new MyLazyFixHighlightVisitor(), myFixture.getTestRootDisposable());
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
      daemon.configureFromFileText("x.java", text);

      MyLazyFixHighlightVisitor.infoCreated = false;
      MyLazyFixHighlightVisitor.fixComputed = false;

      List<HighlightInfo> myWarns = ContainerUtil.filter(
        daemon.waitHighlighting(HighlightSeverity.WARNING), h -> MyLazyFixHighlightVisitor.isMy(h));
      assertEquals(1, myWarns.size(), myWarns::toString);
      assertTrue(MyLazyFixHighlightVisitor.infoCreated);

      CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(project, myFixture.getEditor());
      assertTrue(MyLazyFixHighlightVisitor.fixComputed);

      MyLazyFixHighlightVisitor.infoCreated = false;
      MyLazyFixHighlightVisitor.fixComputed = false;
      daemon.getDaemonCodeAnalyzer().restart(this);
      daemon.waitHighlighting(HighlightSeverity.ERROR);
      assertTrue(MyLazyFixHighlightVisitor.infoCreated);
      CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(project, myFixture.getEditor());
      assertFalse(MyLazyFixHighlightVisitor.fixComputed); // must not recompute on each restart

      Editor editor = myFixture.getEditor();
      WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().setText(""));
      daemon.waitHighlighting(HighlightSeverity.ERROR);
      WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().setText(text));
      EdtTestUtil.runInEdtAndWait(() -> editor.getCaretModel().moveToOffset(editor.getDocument().getText().indexOf("<caret>")));
      MyLazyFixHighlightVisitor.infoCreated = false;
      MyLazyFixHighlightVisitor.fixComputed = false;
      daemon.waitHighlighting(HighlightSeverity.ERROR);
      assertTrue(MyLazyFixHighlightVisitor.infoCreated);
      CodeInsightTestFixtureImpl.waitForLazyQuickFixesUnderCaret(project, myFixture.getEditor());
      assertTrue(MyLazyFixHighlightVisitor.fixComputed); // when text changed too much, it must recompute
    });
  }
}
