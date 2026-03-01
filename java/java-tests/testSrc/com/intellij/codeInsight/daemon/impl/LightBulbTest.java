// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerTestCase;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInsight.intention.impl.IntentionContainer;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * test light bulb behaviour, intention action update and application
 */
@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class LightBulbTest extends ProductionDaemonAnalyzerTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      if (myEditor != null) {
        Document document = myEditor.getDocument();
        FileDocumentManager.getInstance().reloadFromDisk(document);
      }
      Project project = getProject();
      if (project != null) {
        doPostponedFormatting(project);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected Sdk getTestProjectJdk() {
    //noinspection removal
    return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
  }

  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_11;
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    // treat listeners added there as not leaks
    EditorMouseHoverPopupManager.getInstance();
  }

  public void testBulbAppearsAfterType() {
    String text = "class S { ArrayList<caret>XXX x;}";
    configureByText(JavaFileType.INSTANCE, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

    Set<LightweightHint> shown = new ReferenceOpenHashSet<>();
    getProject().getMessageBus().connect().subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
      @Override
      public void hintShown(@NotNull Editor editor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
        shown.add(hint);
        hint.addHintListener(event -> shown.remove(hint));
      }
    });

    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));

    type("x");
    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));
  }

  public void testBulbMustDisappearAfterPressEscape() {
    String text = "class S { ArrayList<caret>XXX x;}";
    configureByText(JavaFileType.INSTANCE, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

    Set<LightweightHint> shown = new ReferenceOpenHashSet<>();
    getProject().getMessageBus().connect().subscribe(EditorHintListener.TOPIC,
                                                     new EditorHintListener() {
                                                       @Override
                                                       public void hintShown(@NotNull Editor editor,
                                                                             @NotNull LightweightHint hint,
                                                                             int flags,
                                                                             @NotNull HintHint hintInfo) {
                                                         shown.add(hint);
                                                         hint.addHintListener(event -> shown.remove(hint));
                                                       }
                                                     });

    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));
    assertTrue(hintComponent.hasVisibleLightBulbOrPopup());

    CommandProcessor.getInstance().executeCommand(getProject(), () -> EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_EDITOR_ESCAPE, true), "", null, getEditor().getDocument());
    myDaemonCodeAnalyzer.restart(getTestName(false));
    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNull(hintComponent);

    // the bulb must reappear when the caret moved
    caretLeft();
    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    IntentionHintComponent hintComponentAfter = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponentAfter);
    assertFalse(hintComponentAfter.isDisposed());
    assertNotNull(hintComponentAfter.getComponentHint());
    assertTrue(shown.contains(hintComponentAfter.getComponentHint()));
    assertTrue(hintComponentAfter.hasVisibleLightBulbOrPopup());
  }

  public void testLightBulbDoesNotUpdateIntentionsInEDT() {
    AtomicInteger updateCount = new AtomicInteger();
    IntentionAction longLongUpdate = new AbstractIntentionAction() {
      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
      }

      @Nls
      @NotNull
      @Override
      public String getText() {
        return "LongAction";
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        updateCount.incrementAndGet();
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        return true;
      }
    };
    IntentionManager.getInstance().addAction(longLongUpdate);
    Disposer.register(getTestRootDisposable(), () -> IntentionManager.getInstance().unregisterIntention(longLongUpdate));
    configureByText(JavaFileType.INSTANCE, "class X { <caret>  }");
    DaemonRespondToChangesTest.makeEditorWindowVisible(new Point(0, 0), myEditor);
    myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    assertTrue("updateCount:"+updateCount, updateCount.get() > 0);
    myDaemonCodeAnalyzer.restart(getTestName(false));
    TestDaemonCodeAnalyzerImpl.runWithReparseDelay(0, () -> {
      for (int i = 0; i < 1000; i++) {
        LOG.debug("i = " + i);
        int updateCount0 = updateCount.get();
        caretRight();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        caretLeft();
        myDaemonCodeAnalyzer.restart(this);
        assertFalse(myDaemonCodeAnalyzer.getFileStatusMap().allDirtyScopesAreNull(myEditor.getDocument(), EditorContextManager.getEditorContext(myEditor, myProject)));
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), getEditor().getDocument());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // wait for a bit more until ShowIntentionsPass.doApplyInformationToEditor() called
        assertNotSame("updateCount0: "+updateCount0+"; updateCount:"+updateCount, updateCount.get(), updateCount0);
      }
    });
  }

  public void testLightBulbIsHiddenWhenFixRangeIsCollapsed() {
    configureByText(JavaFileType.INSTANCE, "class S { void foo() { boolean <selection>var; if (va<caret>r</selection>) {}} }");
    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

    Set<LightweightHint> visibleHints = new ReferenceOpenHashSet<>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
      @Override
      public void hintShown(@NotNull Editor editor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
        visibleHints.add(hint);
        hint.addHintListener(new HintListener() {
          @Override
          public void hintHidden(@NotNull EventObject event) {
            visibleHints.remove(hint);
            hint.removeHintListener(this);
          }
        });
      }
    });

    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    IntentionHintComponent lastHintBeforeDeletion = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(lastHintBeforeDeletion);
    IntentionContainer lastHintIntentions = lastHintBeforeDeletion.getCachedIntentions();
    assertNotNull(lastHintIntentions);
    assertTrue(lastHintIntentions.toString(),
               ContainerUtil.exists(lastHintIntentions.getErrorFixes(), e -> e.getText().equals("Initialize variable 'var'")));

    delete(myEditor);
    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    IntentionHintComponent lastHintAfterDeletion = myDaemonCodeAnalyzer.getLastIntentionHint();
    // it must be either hidden or not have that error anymore
    if (lastHintAfterDeletion == null) {
      assertEmpty(visibleHints);
    }
    else {
      IntentionContainer after = lastHintAfterDeletion.getCachedIntentions();
      assertNotNull(after);
      assertFalse(after.toString(), ContainerUtil.exists(after.getErrorFixes(), e -> e.getText().equals("Initialize variable 'var'")));
    }
  }

  public void testIntentionActionIsAvailableMustBeQueriedOnlyOncePerHighlightingSession() {
    Map<ProgressIndicator, Throwable> isAvailableCalled = new ConcurrentHashMap<>();
    IntentionAction action = new AbstractIntentionAction() {
      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getText() {
        return "My";
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        DaemonProgressIndicator indicator = (DaemonProgressIndicator)ProgressIndicatorProvider.getGlobalProgressIndicator();
        Throwable alreadyCalled = isAvailableCalled.put(indicator, new Throwable());
        if (alreadyCalled != null) {
          throw new IllegalStateException(" .isAvailable() already called in:\n---------------\n" +
                                          ExceptionUtil.getThrowableText(alreadyCalled) + "\n-----------");
        }
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
      }
    };
    IntentionManager.getInstance().addAction(action);
    Disposer.register(getTestRootDisposable(), () -> IntentionManager.getInstance().unregisterIntention(action));

    @Language("JAVA")
    String text = "class X { }";
    configureByText(JavaFileType.INSTANCE, text);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().setText(text));
    myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    myDaemonCodeAnalyzer.restart(getTestName(false));
    myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
  }

  public void testLightBulbMustShowForHighlightInfoGeneratedByDumbAwareHighlightingPassInDumbMode() {
    List<TextEditorHighlightingPassFactory> collected = Collections.synchronizedList(new ArrayList<>());
    List<TextEditorHighlightingPassFactory> applied = Collections.synchronizedList(new ArrayList<>());
    class MyDumbFix extends AbstractIntentionAction implements DumbAware {
      private static final String fixText = "myDumbFix13";
      @Override
      public @NotNull String getText() {
        return fixText;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
      }
    }
    class DumbFac implements TextEditorHighlightingPassFactory, DumbAware {
      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        return new TestDumbAwareHighlightingPassesStartEvenInDumbModePass(editor, psiFile);
      }

      class TestDumbAwareHighlightingPassesStartEvenInDumbModePass extends EditorBoundHighlightingPass implements DumbAware {
        TestDumbAwareHighlightingPassesStartEvenInDumbModePass(Editor editor, PsiFile psiFile) {
          super(editor, psiFile, false);
        }

        @Override
        public void doCollectInformation(@NotNull ProgressIndicator progress) {
          collected.add(DumbFac.this);
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(0, 1).registerFix(new MyDumbFix(), null, null, null, null).create();
          MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(myDocument, myProject, true);
          BackgroundUpdateHighlightersUtil.setHighlightersInRange(myFile.getTextRange(), List.of(info), markup, getId(), HighlightingSessionImpl.getFromCurrentIndicator(myFile));
        }

        @Override
        public void doApplyInformationToEditor() {
          applied.add(DumbFac.this);
        }
      }
    }
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(getProject());
    DumbFac dumbFac = new DumbFac();
    registrar.registerTextEditorHighlightingPass(dumbFac, null, null, false, -1);

    configureByText(PlainTextFileType.INSTANCE, "  ");
    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

    myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    assertSameElements(collected, dumbFac);
    assertSameElements(applied, dumbFac);
    collected.clear();
    applied.clear();
    {
      IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
      List<IntentionActionWithTextCaching> actions = hintComponent.getCachedIntentions().getAllActions();
      assertTrue(actions.toString(), ContainerUtil.exists(actions, a -> a.getText().equals(MyDumbFix.fixText)));
    }

    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, getTestRootDisposable());
    DumbModeTestUtils.runInDumbModeSynchronously(myProject, () -> {
      collected.clear();
      applied.clear();
      type(' ');
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);

      assertSame(dumbFac, assertOneElement(collected));
      assertSame(dumbFac, assertOneElement(applied));

      {
        IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
        List<IntentionActionWithTextCaching> actions = hintComponent.getCachedIntentions().getAllActions();
        assertTrue(actions.toString(), ContainerUtil.exists(actions, a -> a.getText().equals(MyDumbFix.fixText)));
      }
    });
  }

  static class MyDumbFix implements DumbAware, LocalQuickFix {
    private static final String fixText = "myDumbFix13";

    @Override
    public @NotNull String getFamilyName() {
      return "MyDumbFix";
    }

    @Override
    public @NotNull String getName() {
      return fixText;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {

    }
  }
  public static class MyDumbAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    static final String ERR_MSG = "w13";

    public MyDumbAnnotator() {
      iDidIt(); // is not supposed to ever do anything
    }
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment) {
        ProblemDescriptor descriptor = InspectionManager.getInstance(element.getProject())
          .createProblemDescriptor(element, ERR_MSG, true, ProblemHighlightType.ERROR, true);
        holder.newAnnotation(HighlightSeverity.ERROR, ERR_MSG).newLocalQuickFix(new MyDumbFix(), descriptor).registerFix().create();
        iDidIt();
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
    }
  }

  public void testLightBulbMustShowForLocalQuickFixGeneratedByDumbAwareAnnotatorInDumbMode() {
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyDumbAnnotator()}, () -> {
      configureByText(JavaFileType.INSTANCE, """
        class X {
          // <caret>xxx
        }
        """);
      ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
      UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

      HighlightInfo info = assertOneElement(
        myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
      assertEquals(MyDumbAnnotator.ERR_MSG, info.getDescription());
      {
        IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
        List<IntentionActionWithTextCaching> actions = hintComponent.getCachedIntentions().getAllActions();
        assertTrue(actions.toString(), ContainerUtil.exists(actions, a -> a.getText().equals(MyDumbFix.fixText)));
      }

      CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, getTestRootDisposable());
      DumbModeTestUtils.runInDumbModeSynchronously(myProject, () -> {
        myDaemonCodeAnalyzer.restart(getTestName(false));
        HighlightInfo info2 = assertOneElement(
          myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
        assertEquals(MyDumbAnnotator.ERR_MSG, info2.getDescription());

        {
          IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
          List<IntentionActionWithTextCaching> actions = hintComponent.getCachedIntentions().getAllActions();
          assertTrue(actions.toString(), ContainerUtil.exists(actions, a -> a.getText().equals(MyDumbFix.fixText)));
        }
      });
    });
  }

  public void testLightBulbMustShowInDumbModeForDumbAwarePlainIntentionWhichIsNotQuickFix() {
    // try to apply com.intellij.openapi.editor.actions.FlipCommaIntention (which is DumbAware) in dumb mode
    @Language("JAVA")
    String text = """
      class X {
        void foo(int a, int b) {
          foo(b<caret>, a);
        }
      }
      """;
    configureByText(JavaFileType.INSTANCE, text);
    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    {
      IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
      List<IntentionActionWithTextCaching> actions = hintComponent.getCachedIntentions().getAllActions();
      assertTrue(actions.toString(), ContainerUtil.exists(actions, a -> a.getText().equals("Flip ',' (may change semantics)")));
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, getTestRootDisposable());
    DumbModeTestUtils.runInDumbModeSynchronously(myProject, () -> {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      type(' ');
      backspace();
      assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

      IntentionActionWithTextCaching action;
      {
        IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
        List<IntentionActionWithTextCaching> actions = hintComponent.getCachedIntentions().getAllActions();
        action = ContainerUtil.find(actions, a -> a.getText().equals("Flip ',' (may change semantics)"));
        assertNotNull(actions.toString(), action);
      }
      WriteCommandAction.writeCommandAction(getProject()).withName(getTestName(false)).run(() -> action.getAction().invoke(getProject(), getEditor(), getFile()));
      assertEquals("""
                     class X {
                       void foo(int a, int b) {
                         foo(a, b);
                       }
                     }
                     """, getEditor().getDocument().getText());
    });
  }
}
