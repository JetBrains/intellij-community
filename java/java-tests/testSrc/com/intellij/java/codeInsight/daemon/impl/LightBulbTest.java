// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IntentionsUI;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInsight.intention.impl.IntentionContainer;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesFixture.daemonRespondToChangesFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * test light bulb behaviour, intention action update and application
 */
@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
@TestApplication
public class LightBulbTest {
  private static final Logger LOG = Logger.getInstance(LightBulbTest.class);

  private final TestFixture<DaemonRespondToChangesFixture> daemonFixture = daemonRespondToChangesFixture();

  @Test
  public void testBulbAppearsAfterType() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class S { ArrayList<caret>XXX x;}";
      configureFocusedEditor(fixture, JavaFileType.INSTANCE, text, true);

      Set<LightweightHint> shown = new ReferenceOpenHashSet<>();
      fixture.getProject().getMessageBus().connect().subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
        @Override
        public void hintShown(@NotNull Editor editor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
          shown.add(hint);
          hint.addHintListener(event -> shown.remove(hint));
        }
      });

      PsiFile file = fixture.getFile();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        assertThat(testDaemon.waitHighlighting(file, HighlightSeverity.ERROR)).isNotEmpty();

        IntentionHintComponent hintComponent = daemon.getLastIntentionHint();
        assertNotNull(hintComponent);
        assertFalse(hintComponent.isDisposed());
        assertNotNull(hintComponent.getComponentHint());
        assertTrue(shown.contains(hintComponent.getComponentHint()));
      });

      fixture.type("x");
      EdtTestUtil.runInEdtAndWait(() -> {
        assertThat(testDaemon.waitHighlighting(file, HighlightSeverity.ERROR)).isNotEmpty();
        IntentionHintComponent hintComponent = daemon.getLastIntentionHint();
        assertNotNull(hintComponent);
        assertFalse(hintComponent.isDisposed());
        assertNotNull(hintComponent.getComponentHint());
        assertTrue(shown.contains(hintComponent.getComponentHint()));
      });
    });
  }

  @Test
  public void testBulbMustDisappearAfterPressEscape() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class S { ArrayList<caret>XXX x;}";
      configureFocusedEditor(fixture, JavaFileType.INSTANCE, text, true);

      Set<LightweightHint> shown = new ReferenceOpenHashSet<>();
      fixture.getProject().getMessageBus().connect().subscribe(EditorHintListener.TOPIC,
                                                               new EditorHintListener() {
                                                                 @Override
                                                                 public void hintShown(@NotNull Editor editor,
                                                                                       @NotNull LightweightHint hint,
                                                                                       int flags,
                                                                                       @NotNull HintHint hintInfo) {
                                                                   shown.add(hint);
                                                                   hint.addHintListener(_ -> shown.remove(hint));
                                                                 }
                                                               });

      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Editor editor = fixture.getEditor();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      String testName = fixture.getTestName(false);
      EdtTestUtil.runInEdtAndWait(() -> {
        assertThat(testDaemon.waitHighlighting(file, HighlightSeverity.ERROR)).isNotEmpty();

        IntentionHintComponent hintComponent = daemon.getLastIntentionHint();
        assertNotNull(hintComponent);
        assertFalse(hintComponent.isDisposed());
        assertNotNull(hintComponent.getComponentHint());
        assertTrue(shown.contains(hintComponent.getComponentHint()));
        assertTrue(hintComponent.hasVisibleLightBulbOrPopup());
      });

      EdtTestUtil.runInEdtAndWait(() -> {
        CommandProcessor.getInstance().executeCommand(project, () -> EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, true), "", null, editor.getDocument());
        daemon.restart(testName);
        assertThat(testDaemon.waitHighlighting(file, HighlightSeverity.ERROR)).isNotEmpty();
        assertNull(daemon.getLastIntentionHint());
      });

      // the bulb must reappear when the caret moved
      fixture.caretLeft();
      EdtTestUtil.runInEdtAndWait(() -> {
        assertThat(testDaemon.waitHighlighting(file, HighlightSeverity.ERROR)).isNotEmpty();
        IntentionHintComponent hintComponentAfter = daemon.getLastIntentionHint();
        assertNotNull(hintComponentAfter);
        assertFalse(hintComponentAfter.isDisposed());
        assertNotNull(hintComponentAfter.getComponentHint());
        assertTrue(shown.contains(hintComponentAfter.getComponentHint()), "Shown:"+shown+"; hint:"+hintComponentAfter.getComponentHint());
        assertTrue(hintComponentAfter.hasVisibleLightBulbOrPopup());
      });
    });
  }

  @Test
  public void testLastIntentionHintMustBeDroppedAsSoonAsItIsDisposed() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      IntentionHintComponent hintComponent = showBulb(fixture);

      // dispose the hint bypassing IntentionsUIImpl, as e.g. IntentionHintComponent.IntentionPopup.cancelled() does;
      // the last hint reference must not outlive the component, otherwise it would retain the released editor (IJPL-251590)
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        Disposer.dispose(hintComponent);
        assertNull(daemon.getLastIntentionHint());
      });
    });
  }

  @Test
  public void testLastIntentionHintMustBeDroppedForItsOwnEditorOnlyAndSurviveReleaseOfUnrelatedEditor() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      IntentionHintComponent hintComponent = showBulb(fixture);

      Project project = fixture.getProject();
      Editor editor = fixture.getEditor();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        EditorFactory editorFactory = EditorFactory.getInstance();
        Editor unrelated = editorFactory.createEditor(editorFactory.createDocument("unrelated"), project);
        try {
          // DaemonListeners calls this on editorReleased; the hint of another editor must stay (EA-627551)
          IntentionsUI.getInstance(project).invalidateForEditor(unrelated);
          assertSame(hintComponent, daemon.getLastIntentionHint());
          assertFalse(hintComponent.isDisposed());
        }
        finally {
          editorFactory.releaseEditor(unrelated);
        }
      });

      EdtTestUtil.runInEdtAndWait(() -> {
        IntentionsUI.getInstance(project).invalidateForEditor(editor);
        assertNull(daemon.getLastIntentionHint());
        assertTrue(hintComponent.isDisposed());
      });
    });
  }

  private static @NotNull IntentionHintComponent showBulb(@NotNull DaemonRespondToChangesFixture fixture) {
    @Language("JAVA")
    String text = "class S { ArrayList<caret>XXX x;}";
    configureFocusedEditor(fixture, JavaFileType.INSTANCE, text, true);

    PsiFile file = fixture.getFile();
    DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
    TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
    return EdtTestUtil.runInEdtAndGet(() -> {
      assertThat(testDaemon.waitHighlighting(file, HighlightSeverity.ERROR)).isNotEmpty();

      IntentionHintComponent hintComponent = daemon.getLastIntentionHint();
      assertNotNull(hintComponent);
      assertFalse(hintComponent.isDisposed());
      return hintComponent;
    });
  }

  private static void configureFocusedEditor(@NotNull DaemonRespondToChangesFixture fixture,
                                             @NotNull FileType fileType,
                                             @NotNull String text,
                                             boolean enableImportHint) {
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByText(fileType, text);
      Editor editor = fixture.getEditor();
      ((EditorImpl)editor).getScrollPane().getViewport().setSize(1000, 1000);
      if (enableImportHint) {
        DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
      }
      UIUtil.markAsFocused(editor.getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
    });
  }

  @RequiresEdt
  private static void assertLastHintHasAction(@NotNull DaemonCodeAnalyzerImpl daemon, @NotNull String actionText) {
    IntentionHintComponent hintComponent = daemon.getLastIntentionHint();
    List<IntentionActionWithTextCaching> actions = hintComponent.getCachedIntentions().getAllActions();
    assertTrue(ContainerUtil.exists(actions, a -> a.getText().equals(actionText)), actions.toString());
  }

  @Test
  public void testLightBulbDoesNotUpdateIntentionsInEDT() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
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
      Disposer.register(fixture.getTestRootDisposable(), () -> IntentionManager.getInstance().unregisterIntention(longLongUpdate));
      @Language("JAVA")
      String text = "class X { <caret>  }";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Editor editor = fixture.getEditor();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> DaemonRespondToChangesTest.makeEditorWindowVisible(new Point(0, 0), editor));
      fixture.waitHighlighting(HighlightSeverity.ERROR);
      assertTrue(updateCount.get() > 0, "updateCount:"+updateCount);
      daemon.restart(fixture.getTestName(false));
      TestDaemonCodeAnalyzerImpl.runWithReparseDelay(0, () -> {
        EdtTestUtil.runInEdtAndWait(() -> {
          CodeInsightContext editorContext = EditorContextManager.getEditorContext(editor, project);
          for (int i = 0; i < 1000; i++) {
            LOG.debug("i = " + i);
            int updateCount0 = updateCount.get();
            fixture.caretRight();
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            fixture.caretLeft();
            daemon.restart(this);
            assertFalse(daemon.getFileStatusMap().allDirtyScopesAreNullFor(editor.getDocument(), editorContext));
            testDaemon.waitForDaemonToFinish(file);
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // wait for a bit more until ShowIntentionsPass.doApplyInformationToEditor() called
            assertNotSame(updateCount.get(), updateCount0, "updateCount0: "+updateCount0+"; updateCount:"+updateCount);
          }
        });
      });
    });
  }

  @Test
  public void testLightBulbIsHiddenWhenFixRangeIsCollapsed() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class S { void foo() { boolean <selection>var; if (va<caret>r</selection>) {}} }";
      configureFocusedEditor(fixture, JavaFileType.INSTANCE, text, false);

      Set<LightweightHint> visibleHints = new ReferenceOpenHashSet<>();
      fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
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

      PsiFile file = fixture.getFile();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        assertThat(testDaemon.waitHighlighting(file, HighlightSeverity.ERROR)).isNotEmpty();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        IntentionHintComponent lastHintBeforeDeletion = daemon.getLastIntentionHint();
        assertNotNull(lastHintBeforeDeletion);
        IntentionContainer lastHintIntentions = lastHintBeforeDeletion.getCachedIntentions();
        assertNotNull(lastHintIntentions);
        assertTrue(ContainerUtil.exists(lastHintIntentions.getErrorFixes(), e -> e.getText().equals("Initialize variable 'var'")),
                   lastHintIntentions.toString());
      });

      fixture.delete();
      EdtTestUtil.runInEdtAndWait(() -> {
        assertThat(testDaemon.waitHighlighting(file, HighlightSeverity.ERROR)).isNotEmpty();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        IntentionHintComponent lastHintAfterDeletion = daemon.getLastIntentionHint();
        // it must be either hidden or not have that error anymore
        if (lastHintAfterDeletion == null) {
          assertThat(visibleHints).isEmpty();
        }
        else {
          IntentionContainer after = lastHintAfterDeletion.getCachedIntentions();
          assertNotNull(after);
          assertFalse(ContainerUtil.exists(after.getErrorFixes(), e -> e.getText().equals("Initialize variable 'var'")), after.toString());
        }
      });
    });
  }

  @Test
  public void testIntentionActionIsAvailableMustBeQueriedOnlyOncePerHighlightingSession() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
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
      Disposer.register(fixture.getTestRootDisposable(), () -> IntentionManager.getInstance().unregisterIntention(action));

      @Language("JAVA")
      String text = "class X { }";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Editor editor = fixture.getEditor();
      WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> editor.getDocument().setText(text));
      fixture.waitHighlighting(HighlightSeverity.ERROR);
      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      fixture.waitHighlighting(HighlightSeverity.ERROR);
    });
  }

  @Test
  public void testLightBulbMustShowForHighlightInfoGeneratedByDumbAwareHighlightingPassInDumbMode() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      testDaemon.mustWaitForSmartModeByDefault(false);

      Collection<TextEditorHighlightingPassFactory> collected = Collections.synchronizedSet(new HashSet<>());
      Collection<TextEditorHighlightingPassFactory> applied = Collections.synchronizedSet(new HashSet<>());
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
            super(editor, psiFile, true);
          }

          @Override
          public void doCollectInformation(@NotNull ProgressIndicator progress) {
            collected.add(DumbFac.this);
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(0, 1).registerFix(new MyDumbFix(), null, null, null, null).create();
            MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(myDocument, myProject, true);
            BackgroundUpdateHighlightersUtil.setHighlightersInRange(myFile.getTextRange(), List.of(info), markup, getId(), DaemonCodeAnalyzerEx.getInstanceEx(myProject).getHighlightSessionFromCurrentIndicator(myFile));
          }

          @Override
          public void doApplyInformationToEditor() {
            applied.add(DumbFac.this);
          }
        }
      }
      Project project = fixture.getProject();
      TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(project);
      DumbFac dumbFac = new DumbFac();
      registrar.registerTextEditorHighlightingPass(dumbFac, null, null, true, -1);

      configureFocusedEditor(fixture, PlainTextFileType.INSTANCE, "  ", true);
      LOG.debug("=============================");
      PsiFile file = fixture.getFile();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        List<HighlightInfo> errors = testDaemon.waitHighlightingSurviveCancellations(file, HighlightSeverity.ERROR);
        assertThat(errors).hasSize(1);
        HighlightInfo error = errors.get(0);
        assertEquals(new TextRange(0, 1), TextRange.create(error));
        assertNotNull(error.findRegisteredQuickFix((descriptor, _) -> descriptor.getAction() instanceof MyDumbFix fix ? fix : null), error.toString());
        assertThat(collected).containsExactlyInAnyOrder(dumbFac);
        assertThat(applied).containsExactlyInAnyOrder(dumbFac);
        collected.clear();
        applied.clear();
        assertLastHintHasAction(daemon, MyDumbFix.fixText);
      });

      CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, fixture.getTestRootDisposable());
      DumbModeTestUtils.runInDumbModeSynchronously(project, () -> {
        collected.clear();
        applied.clear();
        fixture.type(' ');
        EdtTestUtil.runInEdtAndWait(() -> {
          testDaemon.waitHighlightingSurviveCancellations(file, HighlightSeverity.ERROR);

          assertThat(collected).hasSize(1);
          assertSame(dumbFac, ContainerUtil.getOnlyItem(collected));
          assertThat(applied).hasSize(1);
          assertSame(dumbFac, ContainerUtil.getOnlyItem(applied));

          assertLastHintHasAction(daemon, MyDumbFix.fixText);
        });
      });
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

  @Test
  public void testLightBulbMustShowForLocalQuickFixGeneratedByDumbAwareAnnotatorInDumbMode() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      testDaemon.mustWaitForSmartModeByDefault(false);
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyDumbAnnotator()}, () -> {
        @Language("JAVA")
        String text = """
          class X {
            // <caret>xxx
          }
          """;
        configureFocusedEditor(fixture, JavaFileType.INSTANCE, text, true);

        PsiFile file = fixture.getFile();
        DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
        EdtTestUtil.runInEdtAndWait(() -> {
          List<HighlightInfo> infos = testDaemon.waitHighlightingSurviveCancellations(file, HighlightSeverity.ERROR);
          assertThat(infos).hasSize(1);
          assertEquals(MyDumbAnnotator.ERR_MSG, infos.get(0).getDescription());
          assertLastHintHasAction(daemon, MyDumbFix.fixText);
        });

        CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, fixture.getTestRootDisposable());
        String testName = fixture.getTestName(false);
        DumbModeTestUtils.runInDumbModeSynchronously(fixture.getProject(), () -> {
          EdtTestUtil.runInEdtAndWait(() -> {
            daemon.restart(testName);
            List<HighlightInfo> infos2 = testDaemon.waitHighlightingSurviveCancellations(file, HighlightSeverity.ERROR);
            assertThat(infos2).hasSize(1);
            assertEquals(MyDumbAnnotator.ERR_MSG, infos2.get(0).getDescription());

            assertLastHintHasAction(daemon, MyDumbFix.fixText);
          });
        });
      });
    });
  }

  @Test
  public void testLightBulbMustShowInDumbModeForDumbAwarePlainIntentionWhichIsNotQuickFix() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      testDaemon.mustWaitForSmartModeByDefault(false);
      // try to apply com.intellij.openapi.editor.actions.FlipCommaIntention (which is DumbAware) in dumb mode
      @Language("JAVA")
      String text = """
        class X {
          void foo(int a, int b) {
            foo(b<caret>, a);
          }
        }
        """;
      configureFocusedEditor(fixture, JavaFileType.INSTANCE, text, true);

      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Editor editor = fixture.getEditor();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        assertThat(testDaemon.waitHighlightingSurviveCancellations(file, HighlightSeverity.ERROR)).isEmpty();
        assertLastHintHasAction(daemon, "Flip ',' (may change semantics)");
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      });
      CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, fixture.getTestRootDisposable());
      String testName = fixture.getTestName(false);
      DumbModeTestUtils.runInDumbModeSynchronously(project, () -> {
        EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
        fixture.type(' ');
        fixture.backspace();
        EdtTestUtil.runInEdtAndWait(() -> {
          assertThat(testDaemon.waitHighlightingSurviveCancellations(file, HighlightSeverity.ERROR)).isEmpty();

          IntentionActionWithTextCaching action;
          {
            IntentionHintComponent hintComponent = daemon.getLastIntentionHint();
            List<IntentionActionWithTextCaching> actions = hintComponent.getCachedIntentions().getAllActions();
            action = ContainerUtil.find(actions, a -> a.getText().equals("Flip ',' (may change semantics)"));
            assertNotNull(action, actions.toString());
          }
          WriteCommandAction.writeCommandAction(project).withName(testName).run(() -> action.getAction().invoke(project, editor, file));
        });
        @Language("JAVA")
        String expected = """
          class X {
            void foo(int a, int b) {
              foo(a, b);
            }
          }
          """;
        assertEquals(expected, editor.getDocument().getText());
      });
    });
  }
}
