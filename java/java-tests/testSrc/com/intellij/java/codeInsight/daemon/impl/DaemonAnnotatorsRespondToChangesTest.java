// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.CanChangeDocumentDuringHighlighting;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiPlainText;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.TestTimeOut;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesFixture.daemonRespondToChangesFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * tests {@link Annotator} behaviour during highlighting
 */
@TestApplication
@SkipSlowTestLocally
@CanChangeDocumentDuringHighlighting
public class DaemonAnnotatorsRespondToChangesTest {
  private static final Logger LOG = Logger.getInstance(DaemonAnnotatorsRespondToChangesTest.class);

  private final TestFixture<DaemonRespondToChangesFixture> daemonFixture = daemonRespondToChangesFixture();

  @BeforeEach
  void setUp() {
    PlatformTestUtil.assumeEnoughParallelism();
    UndoManager.getInstance(daemonFixture.get().getProject());
  }

  @AfterEach
  void tearDown() {
    MyRecordingAnnotator.clearAll();
  }

  @Test
  public void testHighlightingDoesWaitForEmbarrassinglySlowExternalAnnotatorsToFinish() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class X { int f() { int gg<caret> = 11; return 0;} }";
      AtomicBoolean run = new AtomicBoolean();
      final int SLEEP = 2_000;
      ExternalAnnotator<Integer, Integer> annotator = new ExternalAnnotator<>() {
        @Override
        public Integer collectInformation(@NotNull PsiFile psiFile) {
          return 0;
        }

        @Override
        public Integer doAnnotate(Integer collectedInfo) {
          TimeoutUtil.sleep(SLEEP);
          return 0;
        }

        @Override
        public void apply(@NotNull PsiFile psiFile, Integer annotationResult, @NotNull AnnotationHolder holder) {
          run.set(true);
        }
      };
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.configureByText(JavaFileType.INSTANCE, text);
        ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(JavaLanguage.INSTANCE, annotator, fixture.getTestRootDisposable());

        long start = System.currentTimeMillis();
        List<HighlightInfo> errors = fixture.getTestDaemonCodeAnalyzer().waitHighlighting(fixture.getFile(), HighlightSeverity.ERROR);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(errors).isEmpty();
        if (!run.get()) {
          fail(ThreadDumper.dumpThreadsToString());
        }
        assertTrue(elapsed >= SLEEP, "Elapsed: " + elapsed);
      });
    });
  }

  @Test
  public void testAddRemoveHighlighterRaceInIncorrectAnnotatorsWhichUseFileRecursiveVisit() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyIncorrectlyRecursiveAnnotator()}, () -> {
        @Language("JAVA")
        String text1 = """
          class X {
            int foo(Object param) {
              if (param == this) return 1;
              return 0;
            }
          }
          """;
        fixture.configureByText(JavaFileType.INSTANCE, text1);
        EdtTestUtil.runInEdtAndWait(() -> {
          ((EditorImpl)fixture.getEditor()).getScrollPane().getViewport().setSize(1000, 1000);
          @NotNull Editor editor = fixture.getEditor();
          assertEquals(fixture.getFile().getTextRange(), editor.calculateVisibleRange());
        });

        List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
        assertThat(infos).hasSize(1);
        assertEquals("XXX", infos.getFirst().getDescription());

        for (int i = 0; i < 100; i++) {
          fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false)+ " "+i);
          infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
          assertThat(infos).hasSize(1);
          assertEquals("XXX", infos.getFirst().getDescription());
        }
      });
    });
  }

  public static <T extends Throwable> void useAnnotatorsIn(@NotNull com.intellij.lang.Language language,
                                     MyRecordingAnnotator @NotNull [] annotators,
                                     @NotNull ThrowableRunnable<T> runnable) throws T {
    useAnnotatorsIn(Collections.singletonMap(language, annotators), runnable);
  }

  private static <T extends Throwable> void useAnnotatorsIn(@NotNull Map<com.intellij.lang.Language, MyRecordingAnnotator @NotNull []> annotatorsByLanguage,
                                      @NotNull ThrowableRunnable<T> runnable) throws T {
    MyRecordingAnnotator.clearAll();
    for (Map.Entry<com.intellij.lang.Language, MyRecordingAnnotator[]> entry : annotatorsByLanguage.entrySet()) {
      com.intellij.lang.Language language = entry.getKey();
      MyRecordingAnnotator[] annotators = entry.getValue();
      for (Annotator annotator : annotators) {
        LanguageAnnotators.INSTANCE.addExplicitExtension(language, annotator);
      }
    }

    try {
      for (Map.Entry<com.intellij.lang.Language, MyRecordingAnnotator[]> entry : annotatorsByLanguage.entrySet()) {
        com.intellij.lang.Language language = entry.getKey();
        MyRecordingAnnotator[] annotators = entry.getValue();
        List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(language);
        assertTrue(list.containsAll(Arrays.asList(annotators)), list.toString());
      }
      runnable.run();
      for (Map.Entry<com.intellij.lang.Language, MyRecordingAnnotator[]> entry : annotatorsByLanguage.entrySet()) {
        MyRecordingAnnotator[] annotators = entry.getValue();
        for (MyRecordingAnnotator annotator : annotators) {
          assertTrue(annotator.didIDoIt(), annotator + " must have done something but didn't");
        }
      }
    }
    finally {
      for (Map.Entry<com.intellij.lang.Language, MyRecordingAnnotator[]> entry : annotatorsByLanguage.entrySet()) {
        com.intellij.lang.Language language = entry.getKey();
        MyRecordingAnnotator[] annotators = entry.getValue();
        for (int i = annotators.length - 1; i >= 0; i--) {
          Annotator annotator = annotators[i];
          LanguageAnnotators.INSTANCE.removeExplicitExtension(language, annotator);
        }
      }
    }

    for (Map.Entry<com.intellij.lang.Language, MyRecordingAnnotator[]> entry : annotatorsByLanguage.entrySet()) {
      com.intellij.lang.Language language = entry.getKey();
      MyRecordingAnnotator[] annotators = entry.getValue();
      List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(language);
      for (Annotator annotator : annotators) {
        assertFalse(list.contains(annotator), list.toString());
      }
    }
  }

  public static class MyIncorrectlyRecursiveAnnotator extends MyRecordingAnnotator {
    Random random = new Random();
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      if (psiElement instanceof PsiFile) {
        psiElement.accept(new JavaRecursiveElementWalkingVisitor(){
          @Override
          public void visitKeyword(@NotNull PsiKeyword keyword) {
            if (Objects.equals(keyword.getText(), "this")) {
              holder.newAnnotation(HighlightSeverity.WARNING, "XXX").range(keyword).create();
              TimeoutUtil.sleep(random.nextInt(100));
              iDidIt();
            }
          }
        });
      }
    }
  }

  private static final AtomicInteger toSleepMs = new AtomicInteger(0);
  public abstract static class MyRecordingAnnotator implements Annotator {
    static final Set<Class<?>> done = ConcurrentCollectionFactory.createConcurrentSet();

    protected void iDidIt() {
      done.add(getClass());
    }
    boolean didIDoIt() {
      return done.contains(getClass());
    }
    static void clearAll() {
      done.clear();
    }
  }
  public static class MySleepyAnnotator extends MyRecordingAnnotator {
    MySleepyAnnotator() {
      iDidIt(); // is not supposed to ever do anything
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiFile) { // must be after MyFastAnnotator annotated the comment
        // use this contrived form to be able to bail out immediately by modifying toSleepMs in the other thread
        while (toSleepMs.addAndGet(-100) > 0) {
          TimeoutUtil.sleep(100);
        }
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
    }
  }
  public static class MyFastAnnotator extends MyRecordingAnnotator {
    private static final String SWEARING = "No swearing";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        holder.newAnnotation(HighlightSeverity.ERROR, SWEARING).range(element).create();
        iDidIt();
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
    }
  }

  // highlight c-style comments
  public static class MyInfoAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && ((PsiComment)element).getTokenType().equals(JavaTokenType.C_STYLE_COMMENT)) {
        holder.newAnnotation(HighlightSeverity.INFORMATION, "comment").create();
        iDidIt();
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
    }
  }

  @Test
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately() {
    daemonFixture.get().runTest(() -> {
      useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyInfoAnnotator(), new MySleepyAnnotator(), new MyFastAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
    });
  }
  @Test
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately1() {
    daemonFixture.get().runTest(() -> {
      useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MySleepyAnnotator(), new MyInfoAnnotator(), new MyFastAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
    });
  }
  @Test
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately2() {
    daemonFixture.get().runTest(() -> {
      useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MySleepyAnnotator(), new MyFastAnnotator(), new MyInfoAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
    });
  }
  @Test
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately3() {
    daemonFixture.get().runTest(() -> {
      // also check in the opposite order in case the order of annotators is important
      useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyFastAnnotator(), new MyInfoAnnotator(), new MySleepyAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
    });
  }

  private void checkSwearingHighlightIsVisibleImmediately() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    @Language("JAVA")
    String text = """
      class X /* */ {
        int foo(Object param) {//XXX
          return 0;
        }/* */
      }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByText(JavaFileType.INSTANCE, text);
      ((EditorImpl)fixture.getEditor()).getScrollPane().getViewport().setSize(1000, 1000);
      @NotNull Editor editor = fixture.getEditor();
      assertEquals(fixture.getFile().getTextRange(), editor.calculateVisibleRange());

      toSleepMs.set(1_000_000);

      MarkupModel markupModel = DocumentMarkupModel.forDocument(editor.getDocument(), fixture.getProject(), true);
      TestTimeOut n = TestTimeOut.setTimeout(100, TimeUnit.SECONDS);
      AtomicInteger called = new AtomicInteger();
      AtomicBoolean success = new AtomicBoolean();
      Runnable checkHighlighted = () -> {
        if (success.get()) return;
        called.incrementAndGet();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        long highlighted = Arrays.stream(markupModel.getAllHighlighters())
          .filter(h->h.isValid())
          .map(highlighter -> HighlightInfo.fromRangeHighlighter(highlighter))
          .filter(Objects::nonNull)
          .filter(info -> MyFastAnnotator.SWEARING.equals(info.getDescription()))
          .count();
        if (highlighted != 0) {
          toSleepMs.set(0);
          success.set(true);
        }
        if (n.timedOut()) {
          toSleepMs.set(0);
          throw new RuntimeException(new TimeoutException(ThreadDumper.dumpThreadsToString()));
        }
      };
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(fixture.getProject());
      PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
      long start = System.currentTimeMillis();
      try {
        fixture.getTestDaemonCodeAnalyzer().waitForDaemonToFinish(fixture.getFile(), checkHighlighted);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      if (!success.get()) {
        List<RangeHighlighter> errors = ContainerUtil.filter(markupModel.getAllHighlighters(), highlighter -> highlighter.isValid() && HighlightInfo.fromRangeHighlighter(highlighter) != null && HighlightInfo.fromRangeHighlighter(highlighter).getSeverity() == HighlightSeverity.ERROR);
        long elapsed = System.currentTimeMillis() - start;

        fail("should have been interrupted. toSleepMs: " + toSleepMs + "; highlights: " + errors + "; called: " + called+"; highlighted in "+elapsed+"ms");
      }
    });
  }

  // highlight //XXX comments
  public static class MyNewBuilderAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        holder.newAnnotation(HighlightSeverity.ERROR, MyFastAnnotator.SWEARING).create();
        iDidIt();
      }
      else if (didIDoIt()) {
        // sleep after creating annotation to emulate a very big annotator which does a great amount of work after registering annotation
        // use this contrived form to be able to bail out immediately by modifying toSleepMs in the other thread
        while (toSleepMs.addAndGet(-100) > 0) {
          TimeoutUtil.sleep(100);
        }
      }
    }
  }

  @Test
  public void testAddAnnotationViaBuilderEntailsCreatingCorrespondingRangeHighlighterImmediately() {
    daemonFixture.get().runTest(() -> {
      useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyNewBuilderAnnotator()}, this::checkSwearingHighlightIsVisibleImmediately);
    });
  }

  private static final AtomicBoolean annotated = new AtomicBoolean();
  private static final AtomicBoolean injectedAnnotated = new AtomicBoolean();
  private static final AtomicBoolean inspected = new AtomicBoolean();

  public static class MySlowAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiFile) {
        String dump = ThreadDumper.dumpThreadsToString();
        assertFalse(injectedAnnotated.get(), "At this moment (while the annotations are still running), the file must not have any of its injected fragments processed. threads:\n" + dump);
        assertFalse(inspected.get(), "At this moment (while the annotations are still running), the file must not have any of its inspections run. threads:\n"+ dump);
        annotated.set(true);
        iDidIt();
      }
    }
  }

  public static class MyInjectedSlowAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      assertTrue(annotated.get(), "File already has to be annotated");
      injectedAnnotated.set(true);
      iDidIt();
    }
  }

  private static class EmptyAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      iDidIt();
    }
  }
  @Test
  public void testTypingMustRescheduleDaemonBackByReparseDelayMillis() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      EmptyAnnotator emptyAnnotator = new EmptyAnnotator();
      TestDaemonCodeAnalyzerImpl.runWithReparseDelay(3000, () -> useAnnotatorsIn(JavaLanguage.INSTANCE, new MyRecordingAnnotator[]{emptyAnnotator}, () -> {
        @Language("JAVA")
        String text = "class X {\n}";
        fixture.configureByText(JavaFileType.INSTANCE, text);
        EdtTestUtil.runInEdtAndWait(() -> {
          ((EditorImpl)fixture.getEditor()).getScrollPane().getViewport().setSize(1000, 1000);
          Editor editor = fixture.getEditor();
          assertEquals(fixture.getFile().getTextRange(), editor.calculateVisibleRange());
        });
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(fixture.getProject());
        fixture.waitHighlighting(HighlightSeverity.INFORMATION);
        MyRecordingAnnotator.clearAll();
        long start = System.currentTimeMillis();
        fixture.type(" import java.lang.*;\n");
        for (int i=0; i<10; i++) {
          TimeoutUtil.sleep(100);
          fixture.type(" ");
          EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
          assertFalse(emptyAnnotator.didIDoIt()); // no highlighting should start
        }
        long typingEnd = System.currentTimeMillis();
        long typingElapsed = typingEnd - start;
        fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertTrue(emptyAnnotator.didIDoIt());
        long end = System.currentTimeMillis();
        long highlightElapsed = end - typingEnd;
        assertTrue(typingElapsed > 1000 && highlightElapsed >= 3000,
                   "; typed in " + typingElapsed + "ms; highlighted in " + highlightElapsed + "ms");
        })
      );
    });
  }

  @Test
  public void testDaemonDoesReportTheFirstProducedAnnotation() {
    daemonFixture.get().runTest(() -> {
      useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyInfoAnnotator()}, () -> checkFirstAnnotation());
    });
  }

  private void checkFirstAnnotation() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    AtomicReference<DaemonCodeAnalyzer.DaemonListener.AnnotatorStatistics> firstStatistics = new AtomicReference<>();
    fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
      new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonAnnotatorStatisticsGenerated(@NotNull AnnotationSession session,
                                                       @NotNull Collection<? extends AnnotatorStatistics> statistics,
                                                       @NotNull PsiFile psiFile) {
          List<? extends AnnotatorStatistics> myStatistics = ContainerUtil.filter(statistics, stat1 -> stat1.annotator instanceof MyInfoAnnotator);
          assertThat(myStatistics).hasSize(1);
          firstStatistics.compareAndExchange(null, myStatistics.getFirst());
        }
      });

    @Language("JAVA")
    String text = """
      class X /* */ {
       // comment
      }
      """;
    fixture.configureByText(JavaFileType.INSTANCE, text);

    fixture.waitHighlighting(HighlightSeverity.INFORMATION);

    DaemonCodeAnalyzer.DaemonListener.AnnotatorStatistics stat = firstStatistics.get();
    assertNotNull(stat);
    assertEquals("comment", stat.firstAnnotation.getMessage());
    assertEquals(HighlightSeverity.INFORMATION, stat.firstAnnotation.getSeverity());
    assertSame(stat.firstAnnotation, stat.lastAnnotation);
    assertTrue(stat.annotatorStartStamp > 0);
    assertTrue(stat.firstAnnotationStamp >= stat.annotatorStartStamp);
    assertTrue(stat.lastAnnotationStamp >= stat.firstAnnotationStamp);
    assertTrue(stat.annotatorFinishStamp >= stat.lastAnnotationStamp);
  }

  private static final String wordToAnnotate = "annotate_here";
  public static class MiddleOfTextAnnotator extends MyRecordingAnnotator {
    static volatile boolean doAnnotate = true;
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiPlainText && doAnnotate) {
        int i = element.getText().indexOf(wordToAnnotate);
        if (i != -1) {
          holder.newAnnotation(HighlightSeverity.WARNING, "warning").range(new TextRange(i, i + wordToAnnotate.length())).create();
          iDidIt();
        }
      }
    }
  }
  @Test
  public void testDaemonMustClearHighlightersInVisibleAreaAfterRestartWhenAnnotatorDoesNotReturnAnyAnnotationsAnymore() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByText(PlainTextFileType.INSTANCE, "blah blah\n".repeat(1000) +
                                                          "<caret>" + wordToAnnotate +
                                                          "\n" +
                                                          "blah blah\n".repeat(1000));
      EditorImpl editor = (EditorImpl)fixture.getEditor();
      editor.getScrollPane().getViewport().setSize(1000, 1000);
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      Point2D visualPoint = editor.offsetToPoint2D(editor.getCaretModel().getOffset());
      editor.getScrollPane().getViewport().setViewPosition(new Point((int)visualPoint.getX(), (int)visualPoint.getY()));
      editor.getScrollPane().getViewport().setExtentSize(new Dimension(100, editor.getPreferredHeight() - (int)visualPoint.getY()));
      ProperTextRange visibleRange = editor.calculateVisibleRange();
      assertTrue(visibleRange.getStartOffset() > 0, visibleRange.toString());
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new MiddleOfTextAnnotator()}, ()->{
        MiddleOfTextAnnotator.doAnnotate = true;
        List<HighlightInfo> infos = testDaemonCodeAnalyzer.waitHighlighting(fixture.getFile(), HighlightSeverity.INFORMATION);
        assertThat(infos).hasSize(1);
        assertEquals("warning", infos.getFirst().getDescription());
        MiddleOfTextAnnotator.doAnnotate = false;
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        assertThat(testDaemonCodeAnalyzer.waitHighlighting(fixture.getFile(), HighlightSeverity.INFORMATION)).isEmpty();
      });
    }));
  }

  private static volatile TextRange expectedVisibleRange;
  public static class CheckVisibleRangeAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      TextRange priorityRange = holder.getCurrentAnnotationSession().getPriorityRange();
      assertEquals(expectedVisibleRange, priorityRange);
      iDidIt();
    }
  }
  @Test
  public void testAnnotatorMustReceiveCorrectVisibleRangeViaItsAnnotationSession() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      String text = "blah blah\n".repeat(1000) +
                    "<caret>" + wordToAnnotate +
                    "\n" +
                    "blah blah\n".repeat(1000);
      fixture.configureByText(PlainTextFileType.INSTANCE, text);
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        EditorImpl editor = (EditorImpl)fixture.getEditor();
        editor.getScrollPane().getViewport().setSize(1000, 1000);
        DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        Point2D caretVisualPoint = editor.offsetToPoint2D(editor.getCaretModel().getOffset());
        editor.getScrollPane().getViewport().setViewPosition(new Point((int)caretVisualPoint.getX(), (int)caretVisualPoint.getY()));
        editor.getScrollPane().getViewport().setExtentSize(new Dimension(100, editor.getPreferredHeight() - (int)caretVisualPoint.getY()));
        ProperTextRange visibleRange = editor.calculateVisibleRange();
        assertTrue(visibleRange.getStartOffset() > 0, visibleRange.toString());
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        expectedVisibleRange = visibleRange;
        useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new CheckVisibleRangeAnnotator()}, ()-> assertThat(
          testDaemonCodeAnalyzer.waitHighlighting(fixture.getFile(), HighlightSeverity.INFORMATION)).isEmpty());
      });
      EdtTestUtil.runInEdtAndWait(() -> {
        EditorImpl editor = (EditorImpl)fixture.getEditor();
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible(editor);
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        expectedVisibleRange = new TextRange(0, editor.getDocument().getTextLength());
        useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new CheckVisibleRangeAnnotator()}, ()-> assertThat(
          testDaemonCodeAnalyzer.waitHighlighting(fixture.getFile(), HighlightSeverity.INFORMATION)).isEmpty());
      });
    });
  }

  // highlight each field, stall every other element
  static class MyFieldSlowAnnotator extends MyRecordingAnnotator {
    static final AtomicReference<String> fieldWarningText = new AtomicReference<>();
    static final AtomicInteger stallMs = new AtomicInteger();
    static final AtomicBoolean finished = new AtomicBoolean();
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiField) {
        holder.newAnnotation(HighlightSeverity.WARNING, fieldWarningText.get()).range(element).create();
        iDidIt();
      }
      else if (element instanceof PsiFile) {
        finished.set(true);
      }
      else {
        // stall every other element to exacerbate latency problems if the order is wrong
        TimeoutUtil.sleep(stallMs.get());
      }
    }
  }
  // highlights all "xxx" comments, but only when there are no comments after it
  static class MyCommentFastAnnotator extends MyRecordingAnnotator {
    static final AtomicBoolean finished = new AtomicBoolean();
    static final String fastToolText = "blah.MyCommentFastAnnotator";
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment) {
        if (element.getText().contains("xxx") && !element.getContainingFile().getText().substring(element.getTextOffset()+2).contains("//")) {
          holder.newAnnotation(HighlightSeverity.WARNING, fastToolText).range(element).create();
          iDidIt();
        }
      }
      else if (element instanceof PsiFile) {
        finished.set(true);
        iDidIt();
      }
    }
  }

  @Test
  public void testAnnotatorMustRemoveItsObsoleteHighlightsImmediatelyAfterFinished() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class LQF {
            // xxx
            int f;<caret>
        }""";
      Project project = fixture.getProject();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      Map<com.intellij.lang.Language, MyRecordingAnnotator[]> annotatorsByLanguage = new HashMap<>();
      annotatorsByLanguage.put(JavaLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyFieldSlowAnnotator(), new MyCommentFastAnnotator()});
      MarkupModelEx model = EdtTestUtil.runInEdtAndGet(() -> {
        fixture.configureByText(JavaFileType.INSTANCE, text);
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)fixture.getEditor()); // get "visible area first" optimization out of the way
        UIUtil.markAsFocused(fixture.getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
        SeverityRegistrar.getSeverityRegistrar(project); //preload inspection profile
        MyFieldSlowAnnotator.fieldWarningText.set("1st run");
        MyFieldSlowAnnotator.finished.set(false);
        MyFieldSlowAnnotator.stallMs.set(0);

        MyCommentFastAnnotator.finished.set(false);

        MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(fixture.getEditor().getDocument(), project, true);

        // both annos should produce their results
        useAnnotatorsIn(annotatorsByLanguage, () -> {
          List<HighlightInfo> infos = testDaemonCodeAnalyzer.waitHighlighting(fixture.getFile(), HighlightSeverity.WARNING);
          assertTrue(ContainerUtil.exists(infos, i -> i.getDescription().equals(MyFieldSlowAnnotator.fieldWarningText.get())), infos.toString());
          assertTrue(ContainerUtil.exists(infos, i -> i.getDescription().equals(MyCommentFastAnnotator.fastToolText)), infos.toString());
          RangeHighlighter[] markers = markupModel.getAllHighlighters();
          assertTrue(ContainerUtil.exists(markers, i -> i.isValid() && HighlightInfo.fromRangeHighlighter(i) != null && MyFieldSlowAnnotator.fieldWarningText.get().equals(HighlightInfo.fromRangeHighlighter(i).getDescription())), Arrays.toString(markers));
          assertTrue(ContainerUtil.exists(markers, i -> i.isValid() && HighlightInfo.fromRangeHighlighter(i) != null && MyCommentFastAnnotator.fastToolText.equals(HighlightInfo.fromRangeHighlighter(i).getDescription())), Arrays.toString(markers));
        });
        return markupModel;
      });

      MyFieldSlowAnnotator.fieldWarningText.set("Aha, field, finally!");
      MyFieldSlowAnnotator.stallMs.set(100);
      EdtTestUtil.runInEdtAndWait(() -> {
        // type another comment which will cause the warning about the first comment (by MyCommentFastAnnotator) to disappear
        // and check that as soon as MyCommentFastAnnotator is finished, it removed its own obsolete warnings, whereas MyFieldSlowAnnotator continues to run
        fixture.type("// another comment");
        MyCommentFastAnnotator.finished.set(false);
        MyFieldSlowAnnotator.finished.set(false);
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)fixture.getEditor()); // get "visible area first" optimization out of the way
        LOG.trace("start waiting for fast/slow tools");
        try {
          useAnnotatorsIn(annotatorsByLanguage, () -> {
            // now when the highlighting is restarted, we should get back our anno result very fast, despite very slow processing of every other element
            AtomicBoolean fastToolFinishedFaster = new AtomicBoolean();
            testDaemonCodeAnalyzer.waitForDaemonToFinish(fixture.getFile(), () -> {
              if (MyCommentFastAnnotator.finished.get() && !MyFieldSlowAnnotator.finished.get()) {
                boolean fastToolWarningFound = !DaemonCodeAnalyzerEx.processHighlights(model, project, HighlightSeverity.WARNING, 0,
                                                                                       fixture.getEditor().getDocument().getTextLength(),
                                                                                       info -> !MyCommentFastAnnotator.fastToolText.equals(
                                                                                         info.getDescription()));
                fastToolFinishedFaster.set(true);
                if (fastToolWarningFound) {
                  fail("Annotator must have removed its own obsolete highlights as soon as it's finished, but got:" +
                       StringUtil.join(model.getAllHighlighters(), Object::toString, "\n   ") +
                       "; thread dump:\n" +
                       ThreadDumper.dumpThreadsToString());
                }
              }
            });
            assertTrue(fastToolFinishedFaster.get(), "Fast annotator must have finished faster than the slow one, but it didn't");
          });
        }
        finally {
          MyFieldSlowAnnotator.stallMs.set(0);
        }
      });
    });
  }

  // highlight all "xxx" comments
  static class MyComment1Annotator extends MyRecordingAnnotator {
    static final AtomicBoolean stall = new AtomicBoolean();
    static final String comment1Text = "comment1Text";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      while (stall.get()) {
        Thread.yield();
        //ProgressManager.checkCanceled();
      }
      if (element instanceof PsiComment) {
        if (element.getText().contains("xxx")) {
          holder.newAnnotation(HighlightSeverity.WARNING, comment1Text).range(element).create();
          iDidIt();
          //stall1.set(true); // stall right after producing annotation
        }
      }
    }
  }
  static class MyComment2Annotator extends MyRecordingAnnotator {
    static final AtomicBoolean stall = new AtomicBoolean();
    static final String comment2Text = "comment2Text";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      while (stall.get()) {
        Thread.yield();
        //ProgressManager.checkCanceled();
      }
      if (element instanceof PsiComment) {
        if (element.getText().contains("xxx")) {
          holder.newAnnotation(HighlightSeverity.WARNING, comment2Text).range(element).create();
          iDidIt();
        }
      }
    }
  }

  @Test
  public void testAnnotatorsMustNotWaitForEachOther() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class LQF {
            // xxx
            int f;
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Project project = fixture.getProject();
      PsiFile psiFile = fixture.getFile();
      Editor editor = fixture.getEditor();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      MarkupModelEx model = EdtTestUtil.runInEdtAndGet(() -> {
        DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)editor); // get "visible area first" optimization out of the way
        UIUtil.markAsFocused(editor.getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
        return (MarkupModelEx)DocumentMarkupModel.forDocument(editor.getDocument(), project, true);
      });

      Map<com.intellij.lang.Language, MyRecordingAnnotator @NotNull []> annotatorsByLanguage = new HashMap<>();
      annotatorsByLanguage.put(JavaLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyComment1Annotator(), new MyComment2Annotator()});

      // both annos should produce their results
      EdtTestUtil.runInEdtAndWait(() -> DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)editor)); // get "visible area first" optimization out of the way
      useAnnotatorsIn(annotatorsByLanguage, () -> {
        MyComment1Annotator.stall.set(true);
        MyComment2Annotator.stall.set(false);
        AtomicBoolean tool1AnnoFound = new AtomicBoolean();
        AtomicBoolean tool2AnnoFound = new AtomicBoolean();
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        EdtTestUtil.runInEdtAndWait(() -> testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile, ()->{
          tool1AnnoFound.set(!DaemonCodeAnalyzerEx.processHighlights(model, project, HighlightSeverity.WARNING, 0,
                                                                   editor.getDocument().getTextLength(),
                                                                   info -> !MyComment1Annotator.comment1Text.equals(info.getDescription())));
          tool2AnnoFound.set(!DaemonCodeAnalyzerEx.processHighlights(model, project, HighlightSeverity.WARNING, 0,
                                                                   editor.getDocument().getTextLength(),
                                                                   info -> !MyComment2Annotator.comment2Text.equals(info.getDescription())));
          // we must have Annotator2 results eventually, even though the Annotator1 was stalled
          if (tool2AnnoFound.get()) {
            MyComment1Annotator.stall.set(false);
          }
        }));

        // repeat in the other order in case it matters
        MyComment1Annotator.stall.set(false);
        MyComment2Annotator.stall.set(true);
        tool1AnnoFound.set(false);
        tool2AnnoFound.set(false);
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        EdtTestUtil.runInEdtAndWait(() -> testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile, ()->{
          tool1AnnoFound.set(!DaemonCodeAnalyzerEx.processHighlights(model, project, HighlightSeverity.WARNING, 0,
                                                                   editor.getDocument().getTextLength(),
                                                                   info -> !MyComment1Annotator.comment1Text.equals(info.getDescription())));
          tool2AnnoFound.set(!DaemonCodeAnalyzerEx.processHighlights(model, project, HighlightSeverity.WARNING, 0,
                                                                   editor.getDocument().getTextLength(),
                                                                   info -> !MyComment2Annotator.comment2Text.equals(info.getDescription())));
          // we must have Annotator1 results eventually, even though the Annotator2 was stalled
          if (tool1AnnoFound.get()) {
            MyComment2Annotator.stall.set(false);
          }
        }));
      });
    });
  }

  // highlight "xxx" substring
  public static class MyTextAnnotator extends MyRecordingAnnotator {
    private static final String SWEARING = "xxx substring";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiFile) {
        int i = element.getText().indexOf("xxx");
        if (i != -1) {
          holder.newAnnotation(HighlightSeverity.ERROR, SWEARING).range(new TextRange(i,i+3)).create();
          iDidIt();
        }
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
    }
  }
  @Test
  public void testCloseReopenDoesNotDuplicateWarnings() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyTextAnnotator()}, () -> {
        fixture.configureByText(PlainTextFileType.INSTANCE, "sssxxxsss");
        List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertThat(infos).hasSize(1);
        assertEquals(MyTextAnnotator.SWEARING, infos.getFirst().getDescription());

        PsiFile psiFile = fixture.getFile();
        Document document = ReadAction.computeBlocking(() -> psiFile.getFileDocument());
        VirtualFile virtualFile = psiFile.getVirtualFile();
        EdtTestUtil.runInEdtAndWait(() -> {
          FileEditorManager.getInstance(fixture.getProject()).closeFile(virtualFile);
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        });

        fixture.configureByExistingFile(virtualFile);
        assertSame(document, fixture.getEditor().getDocument());
        List<HighlightInfo> infos2 = fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertThat(infos2).hasSize(1);
        assertEquals(MyTextAnnotator.SWEARING, infos2.getFirst().getDescription());
      });
    });
  }

  public static class MyFileLevelAnnotator extends MyRecordingAnnotator {
    private static final String MSG = "xxx";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiFile) {
        holder.newAnnotation(HighlightSeverity.ERROR, MSG).fileLevel().create();
        iDidIt();
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
    }
  }

  private static @NotNull HighlightInfo getOnlyHighlight(@NotNull List<HighlightInfo> infos) {
    assertThat(infos).hasSize(1);
    return infos.getFirst();
  }

  @Test
  public void testFileLevelAnnotationDoesNotDuplicateOnTyping() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByText(PlainTextFileType.INSTANCE, "text<caret>");
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();

      useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyFileLevelAnnotator()}, () -> {
        for (int i=0; i<100; i++) {
          assertEquals(MyFileLevelAnnotator.MSG, getOnlyHighlight(
            testDaemonCodeAnalyzer.waitHighlighting(fixture.getFile(), HighlightSeverity.ERROR)).getDescription());
          HighlightInfo info = getOnlyHighlight(fixture.getDaemonCodeAnalyzer().getFileLevelHighlights(fixture.getProject(), fixture.getFile()));
          assertEquals(MyFileLevelAnnotator.MSG, info.getDescription());

          fixture.type('2');
          assertEquals(MyFileLevelAnnotator.MSG, getOnlyHighlight(
            testDaemonCodeAnalyzer.waitHighlighting(fixture.getFile(), HighlightSeverity.ERROR)).getDescription());
          info = getOnlyHighlight(fixture.getDaemonCodeAnalyzer().getFileLevelHighlights(fixture.getProject(), fixture.getFile()));
          assertEquals(MyFileLevelAnnotator.MSG, info.getDescription());

          fixture.backspace();
        }
      });
    }));
  }

  public static class MyFileLevelAnnotatorWithConstantlyChangingDescription extends MyRecordingAnnotator {
    private static final String MSG = "xxxzz: ";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiFile) {
        holder.newAnnotation(HighlightSeverity.ERROR, MSG+element.getText().substring(0, Math.min(10, element.getTextLength()))).fileLevel().create();
        iDidIt();
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
    }
    static boolean isMine(HighlightInfo info) {
      return info.getDescription().startsWith(MSG);
    }
  }

  @Test
  public void testFileLevelAnnotationDoesNotDuplicateOnTypingEventWhenItsDescriptionIsConstantlyChanging() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByText(PlainTextFileType.INSTANCE, "<caret>");
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();

      useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyFileLevelAnnotatorWithConstantlyChangingDescription()}, () -> {
        for (int i=0; i<100; i++) {
          assertTrue(MyFileLevelAnnotatorWithConstantlyChangingDescription.isMine(getOnlyHighlight(
            testDaemonCodeAnalyzer.waitHighlighting(fixture.getFile(), HighlightSeverity.ERROR))));
          HighlightInfo info = getOnlyHighlight(fixture.getDaemonCodeAnalyzer().getFileLevelHighlights(fixture.getProject(), fixture.getFile()));
          assertTrue(MyFileLevelAnnotatorWithConstantlyChangingDescription.isMine(info));

          fixture.type('2');
          assertTrue(MyFileLevelAnnotatorWithConstantlyChangingDescription.isMine(getOnlyHighlight(
            testDaemonCodeAnalyzer.waitHighlighting(fixture.getFile(), HighlightSeverity.ERROR))));
          info = getOnlyHighlight(fixture.getDaemonCodeAnalyzer().getFileLevelHighlights(fixture.getProject(), fixture.getFile()));
          assertTrue(MyFileLevelAnnotatorWithConstantlyChangingDescription.isMine(info));

          fixture.backspace();
        }
      });
    }));
  }

  public static class MyBatchAnnotator extends MyRecordingAnnotator {
    private static final String MSG = "batch annotator";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiFile) {
        holder.newAnnotation(HighlightSeverity.WARNING, MSG).range(element).create();
        iDidIt();
      }
    }
  }

  public static class MyEditorOnlyAnnotator extends MyRecordingAnnotator {
    private static final String MSG = "editor-only annotator";

    @Override
    public boolean runInBatchMode() {
      return false;
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiFile) {
        holder.newAnnotation(HighlightSeverity.WARNING, MSG).range(element).create();
        iDidIt();
      }
    }
  }

  @Test
  public void testAnnotatorsWithRunInBatchModeFalseRunInEditor() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      MyBatchAnnotator batchAnnotator = new MyBatchAnnotator();
      MyEditorOnlyAnnotator editorOnlyAnnotator = new MyEditorOnlyAnnotator();
      useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{batchAnnotator, editorOnlyAnnotator}, () -> {
        fixture.configureByText(PlainTextFileType.INSTANCE, "text");
        List<HighlightInfo> warnings = fixture.waitHighlighting(HighlightSeverity.WARNING);
        assertThat(warnings).hasSize(2);
        assertTrue(ContainerUtil.exists(warnings, info -> MyBatchAnnotator.MSG.equals(info.getDescription())));
        assertTrue(ContainerUtil.exists(warnings, info -> MyEditorOnlyAnnotator.MSG.equals(info.getDescription())));
      });
    });
  }

  @Test
  public void testAnnotatorsWithRunInBatchModeFalseAreSkippedInBatchMode() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      MyRecordingAnnotator.clearAll();
      MyBatchAnnotator batchAnnotator = new MyBatchAnnotator();
      MyEditorOnlyAnnotator editorOnlyAnnotator = new MyEditorOnlyAnnotator();
      fixture.configureByText(PlainTextFileType.INSTANCE, "text");
      fixture.waitHighlighting(HighlightSeverity.WARNING);
      LanguageAnnotators.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, batchAnnotator);
      LanguageAnnotators.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, editorOnlyAnnotator);
      try {
        GlobalInspectionToolWrapper toolWrapper = new GlobalInspectionToolWrapper(new HighlightVisitorBasedInspection().setRunAnnotators(true));
        AnalysisScope scope = new AnalysisScope(fixture.getFile());
        GlobalInspectionContextForTests globalContext =
          InspectionsKt.createGlobalContextForTool(scope, fixture.getProject(), List.of(toolWrapper));

        List<CommonProblemDescriptor> descriptors = EdtTestUtil.runInEdtAndGet(() -> {
          InspectionTestUtil.runTool(toolWrapper, scope, globalContext);
          return List.copyOf(globalContext.getPresentation(toolWrapper).getProblemDescriptors());
        });

        assertThat(descriptors).hasSize(1);
        String description = descriptors.getFirst().getDescriptionTemplate();
        assertTrue(description.startsWith(MyBatchAnnotator.MSG), description);
        assertTrue(batchAnnotator.didIDoIt());
        assertFalse(editorOnlyAnnotator.didIDoIt());
      }
      finally {
        LanguageAnnotators.INSTANCE.removeExplicitExtension(PlainTextLanguage.INSTANCE, editorOnlyAnnotator);
        LanguageAnnotators.INSTANCE.removeExplicitExtension(PlainTextLanguage.INSTANCE, batchAnnotator);
      }
    });
  }
}
