// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.TestTimeOut;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * tests {@link Annotator} behaviour during highlighting
 */
@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class DaemonAnnotatorsRespondToChangesTest extends DaemonAnalyzerTestCase {
  private DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    UndoManager.getInstance(myProject);
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
    DaemonProgressIndicator.setDebug(true);
    PlatformTestUtil.assumeEnoughParallelism();
  }

  @Override
  protected void tearDown() throws Exception {
    MyRecordingAnnotator.clearAll();
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
      myDaemonCodeAnalyzer = null;
      super.tearDown();
    }
  }

  @Override
  protected void configureByExistingFile(@NotNull VirtualFile virtualFile) {
    super.configureByExistingFile(virtualFile);
    setActiveEditors(getEditor());
  }

  @Override
  protected VirtualFile configureByFiles(@Nullable File rawProjectRoot, VirtualFile @NotNull ... vFiles) throws IOException {
    VirtualFile file = super.configureByFiles(rawProjectRoot, vFiles);
    setActiveEditors(getEditor());
    return file;
  }

  private void setActiveEditors(Editor @NotNull ... editors) {
    EditorTracker.Companion.getInstance(myProject).setActiveEditors(Arrays.asList(editors));
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

  public void testHighlightingDoesWaitForEmbarrassinglySlowExternalAnnotatorsToFinish() {
    configureByText(JavaFileType.INSTANCE, "class X { int f() { int gg<caret> = 11; return 0;} }");
    AtomicBoolean run = new AtomicBoolean();
    final int SLEEP = 2_000;
    ExternalAnnotator<Integer, Integer> annotator = new ExternalAnnotator<>() {
      @Override
      public Integer collectInformation(@NotNull PsiFile file) {
        return 0;
      }

      @Override
      public Integer doAnnotate(Integer collectedInfo) {
        TimeoutUtil.sleep(SLEEP);
        return 0;
      }

      @Override
      public void apply(@NotNull PsiFile file, Integer annotationResult, @NotNull AnnotationHolder holder) {
        run.set(true);
      }
    };
    ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(JavaLanguage.INSTANCE, annotator, getTestRootDisposable());

    long start = System.currentTimeMillis();
    List<HighlightInfo> errors = filter(CodeInsightTestFixtureImpl.instantiateAndRun(getFile(), getEditor(), new int[0], false),
                                        HighlightSeverity.ERROR);
    long elapsed = System.currentTimeMillis() - start;

    assertEquals(0, errors.size());
    if (!run.get()) {
      fail(ThreadDumper.dumpThreadsToString());
    }
    assertTrue("Elapsed: "+elapsed, elapsed >= SLEEP);
  }

  public void testAddRemoveHighlighterRaceInIncorrectAnnotatorsWhichUseFileRecursiveVisit() {
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
      configureByText(JavaFileType.INSTANCE, text1);
      ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
      @NotNull Editor editor = getEditor();
      assertEquals(getFile().getTextRange(), editor.calculateVisibleRange());

      assertEquals("XXX", assertOneElement(doHighlighting(HighlightSeverity.WARNING)).getDescription());

      for (int i = 0; i < 100; i++) {
        myDaemonCodeAnalyzer.restart();
        List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
        assertEquals("XXX", assertOneElement(infos).getDescription());
      }
    });
  }

  public static void useAnnotatorsIn(@NotNull com.intellij.lang.Language language,
                                     MyRecordingAnnotator @NotNull [] annotators,
                                     @NotNull Runnable runnable) {
    useAnnotatorsIn(Collections.singletonMap(language, annotators), runnable);
  }

  private static void useAnnotatorsIn(@NotNull Map<com.intellij.lang.Language, MyRecordingAnnotator @NotNull []> annotatorsByLanguage,
                                      @NotNull Runnable runnable) {
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
        assertTrue(list.toString(), list.containsAll(Arrays.asList(annotators)));
      }
      runnable.run();
      for (Map.Entry<com.intellij.lang.Language, MyRecordingAnnotator[]> entry : annotatorsByLanguage.entrySet()) {
        MyRecordingAnnotator[] annotators = entry.getValue();
        for (MyRecordingAnnotator annotator : annotators) {
          assertTrue(annotator + " must have done something but didn't", annotator.didIDoIt());
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
        assertFalse(list.toString(), list.contains(annotator));
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
    public MySleepyAnnotator() {
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

  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately() {
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyInfoAnnotator(), new MySleepyAnnotator(), new MyFastAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
  }
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately1() {
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MySleepyAnnotator(), new MyInfoAnnotator(), new MyFastAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
  }
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately2() {
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MySleepyAnnotator(), new MyFastAnnotator(), new MyInfoAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
  }
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately3() {
    // also check in the opposite order in case the order of annotators is important
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyFastAnnotator(), new MyInfoAnnotator(), new MySleepyAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
  }

  private void checkSwearingHighlightIsVisibleImmediately() {
    @Language("JAVA")
    String text = """
      class X /* */ {
        int foo(Object param) {//XXX
          return 0;
        }/* */
      }
      """;
    configureByText(JavaFileType.INSTANCE, text);
    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    @NotNull Editor editor = getEditor();
    assertEquals(getFile().getTextRange(), editor.calculateVisibleRange());

    toSleepMs.set(1_000_000);

    MarkupModel markupModel = DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);
    TestTimeOut n = TestTimeOut.setTimeout(100, TimeUnit.SECONDS);
    AtomicInteger called = new AtomicInteger();
    AtomicBoolean success = new AtomicBoolean();
    Runnable checkHighlighted = () -> {
      if (success.get()) return;
      called.incrementAndGet();
      UIUtil.dispatchAllInvocationEvents();
      long highlighted = Arrays.stream(markupModel.getAllHighlighters())
        .map(highlighter -> HighlightInfo.fromRangeHighlighter(highlighter))
        .filter(Objects::nonNull)
        .filter(info -> MyFastAnnotator.SWEARING.equals(info.getDescription()))
        .count();
      if (highlighted != 0) {
        toSleepMs.set(0);
        success.set(true);
        //throw new DebugException(); // sorry for that, had to differentiate from failure
      }
      if (n.timedOut()) {
        toSleepMs.set(0);
        throw new RuntimeException(new TimeoutException(ThreadDumper.dumpThreadsToString()));
      }
    };
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    long start = System.currentTimeMillis();
    myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), textEditor, ArrayUtilRt.EMPTY_INT_ARRAY, false, checkHighlighted);
    if (!success.get()) {
      List<RangeHighlighter> errors = ContainerUtil.filter(markupModel.getAllHighlighters(), highlighter -> HighlightInfo.fromRangeHighlighter(highlighter) != null && HighlightInfo.fromRangeHighlighter(highlighter).getSeverity() == HighlightSeverity.ERROR);
      long elapsed = System.currentTimeMillis() - start;

      fail("should have been interrupted. toSleepMs: " + toSleepMs + "; highlights: " + errors + "; called: " + called+"; highlighted in "+elapsed+"ms");
    }
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

  public void testAddAnnotationViaBuilderEntailsCreatingCorrespondingRangeHighlighterImmediately() {
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyNewBuilderAnnotator()}, this::checkSwearingHighlightIsVisibleImmediately);
  }

  private static final AtomicBoolean annotated = new AtomicBoolean();
  private static final AtomicBoolean injectedAnnotated = new AtomicBoolean();
  private static final AtomicBoolean inspected = new AtomicBoolean();

  public static class MySlowAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiFile) {
        String dump = ThreadDumper.dumpThreadsToString();
        assertFalse("At this moment (while the annotations are still running), the file must not have any of its injected fragments processed. threads:\n" + dump, injectedAnnotated.get());
        assertFalse("At this moment (while the annotations are still running), the file must not have any of its inspections run. threads:\n"+ dump, inspected.get());
        annotated.set(true);
        iDidIt();
      }
    }
  }

  public static class MyInjectedSlowAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      assertTrue("File already has to be annotated", annotated.get());
      injectedAnnotated.set(true);
      iDidIt();
    }
  }

  public void test_SerializeCodeInsightPasses_SecretSettingDoesWork() {
    TextEditorHighlightingPassRegistrarImpl registrar =
      (TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(myProject);
    assertFalse("Somebody (rogue plugin?) has left the dangerous setting on", registrar.isSerializeCodeInsightPasses());

    enableInspectionTool(new LocalInspectionTool() {
      @Override
      public @NotNull String getID() {
        return getTestName(false)+"MySlowInspectionTool";
      }

      @Override
      public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                     boolean isOnTheFly,
                                                     @NotNull LocalInspectionToolSession session) {
        return new PsiElementVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            assertTrue("File has to be already annotated", annotated.get());
            inspected.set(true);
          }
        };
      }
    });
    annotated.set(false);
    injectedAnnotated.set(false);
    inspected.set(false);
    try {
      myDaemonCodeAnalyzer.serializeCodeInsightPasses(true);

      Map<com.intellij.lang.Language, MyRecordingAnnotator @NotNull []> annotatorsByLanguage = new HashMap<>();
      annotatorsByLanguage.put(JavaLanguage.INSTANCE, new MyRecordingAnnotator[]{new MySlowAnnotator()});
      annotatorsByLanguage.put(XMLLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyInjectedSlowAnnotator()});

      useAnnotatorsIn(annotatorsByLanguage, () -> {
        configureByText(JavaFileType.INSTANCE,
                        """
                          class X{
                          // language=XML
                          String ql = "<value>1</value>";
                          }""");

        doHighlighting();
        assertTrue("File already has to be java annotated", annotated.get());
        assertTrue("File already has to annotate xml injection", injectedAnnotated.get());
        assertTrue("File already has to run inspections", inspected.get());
      });
    }
    finally {
      myDaemonCodeAnalyzer.serializeCodeInsightPasses(false);
      annotated.set(false);
      injectedAnnotated.set(false);
      inspected.set(false);
    }
  }

  static class EmptyAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      iDidIt();
    }
  }
  public void testTypingMustRescheduleDaemonBackByReparseDelayMillis() {
    EmptyAnnotator emptyAnnotator = new EmptyAnnotator();
    DaemonRespondToChangesTest.runWithReparseDelay(2000, () -> useAnnotatorsIn(JavaLanguage.INSTANCE, new MyRecordingAnnotator[]{emptyAnnotator}, () -> {
            @Language("JAVA")
            String text = "class X {\n}";
            configureByText(JavaFileType.INSTANCE, text);
            ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
      @NotNull Editor editor = getEditor();
      assertEquals(getFile().getTextRange(), editor.calculateVisibleRange());
            CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());
            doHighlighting();
            MyRecordingAnnotator.clearAll();
            type(" import java.lang.*;\n");
            long start = System.currentTimeMillis();
            for (int i=0; i<10; i++) {
              type(" ");
              TimeoutUtil.sleep(100);
              UIUtil.dispatchAllInvocationEvents();
            }
            long typing = System.currentTimeMillis();
            while (!emptyAnnotator.didIDoIt()) {
              UIUtil.dispatchAllInvocationEvents();
            }
            long end = System.currentTimeMillis();

            long typingElapsed = typing - start;
            long highlightElapsed = end - typing;
            assertTrue("; typed in " + typingElapsed + "ms; highlighted in " + highlightElapsed + "ms",
                       typingElapsed > 1000 && highlightElapsed >= 2000);
          })
    );
  }

  public void testDaemonDoesReportTheFirstProducedAnnotation() {
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyInfoAnnotator()}, () -> checkFirstAnnotation());
  }

  private void checkFirstAnnotation() {
    AtomicReference<DaemonCodeAnalyzer.DaemonListener.AnnotatorStatistics> firstStatistics = new AtomicReference<>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
      new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonAnnotatorStatisticsGenerated(@NotNull AnnotationSession session,
                                                       @NotNull Collection<? extends AnnotatorStatistics> statistics,
                                                       @NotNull PsiFile file) {
          AnnotatorStatistics stat = assertOneElement(ContainerUtil.filter(statistics, stat1 -> stat1.annotator instanceof MyInfoAnnotator));
          firstStatistics.compareAndExchange(null, stat);
        }
      });

    @Language("JAVA")
    String text = """
      class X /* */ {
       // comment
      }
      """;
    configureByText(JavaFileType.INSTANCE, text);

    doHighlighting();

    DaemonCodeAnalyzer.DaemonListener.AnnotatorStatistics stat = firstStatistics.get();
    assertNotNull(stat);
    assertEquals("Annotation(message='comment', severity='INFORMATION', toolTip='<html>comment</html>')", stat.firstAnnotation.toString());
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
  public void testDaemonMustClearHighlightersInVisibleAreaAfterRestartWhenAnnotatorDoesNotReturnAnyAnnotationsAnymore() {
    configureByText(PlainTextFileType.INSTANCE, "blah blah\n".repeat(1000) +
                                                "<caret>" + wordToAnnotate +
                                                "\n" +
                                                "blah blah\n".repeat(1000));
    EditorImpl editor = (EditorImpl)getEditor();
    editor.getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    Point2D visualPoint = editor.offsetToPoint2D(editor.getCaretModel().getOffset());
    editor.getScrollPane().getViewport().setViewPosition(new Point((int)visualPoint.getX(), (int)visualPoint.getY()));
    editor.getScrollPane().getViewport().setExtentSize(new Dimension(100, editor.getPreferredHeight() - (int)visualPoint.getY()));
    ProperTextRange visibleRange = editor.calculateVisibleRange();
    assertTrue(visibleRange.toString(), visibleRange.getStartOffset() > 0);
    useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new MiddleOfTextAnnotator()}, ()->{
      MiddleOfTextAnnotator.doAnnotate = true;
      List<HighlightInfo> infos = doHighlighting();
      HighlightInfo info = assertOneElement(infos);
      assertEquals("warning", info.getDescription());
      MiddleOfTextAnnotator.doAnnotate = false;
      myDaemonCodeAnalyzer.restart();
      assertEmpty(doHighlighting());
    });
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
  public void testAnnotatorMustReceiveCorrectVisibleRangeViaItsAnnotationSession() {
    String text = "blah blah\n".repeat(1000) +
                  "<caret>" + wordToAnnotate +
                  "\n" +
                  "blah blah\n".repeat(1000);
    configureByText(PlainTextFileType.INSTANCE, text);
    EditorImpl editor = (EditorImpl)getEditor();
    editor.getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    Point2D caretVisualPoint = editor.offsetToPoint2D(editor.getCaretModel().getOffset());
    editor.getScrollPane().getViewport().setViewPosition(new Point((int)caretVisualPoint.getX(), (int)caretVisualPoint.getY()));
    editor.getScrollPane().getViewport().setExtentSize(new Dimension(100, editor.getPreferredHeight() - (int)caretVisualPoint.getY()));
    ProperTextRange visibleRange = editor.calculateVisibleRange();
    assertTrue(visibleRange.toString(), visibleRange.getStartOffset() > 0);
    myDaemonCodeAnalyzer.restart();
    expectedVisibleRange = visibleRange;
    useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new CheckVisibleRangeAnnotator()}, ()-> assertEmpty(doHighlighting()));
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible(editor);
    myDaemonCodeAnalyzer.restart();
    expectedVisibleRange = new TextRange(0, editor.getDocument().getTextLength());
    useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new CheckVisibleRangeAnnotator()}, ()-> assertEmpty(doHighlighting()));
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

  public void testAnnotatorMustRemoveItsObsoleteHighlightsImmediatelyAfterFinished() {
    @Language("JAVA")
    String text = """
      class LQF {
          // xxx
          int f;<caret>
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)myEditor); // get "visible area first" optimization out of the way
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
    SeverityRegistrar.getSeverityRegistrar(getProject()); //preload inspection profile
    MyFieldSlowAnnotator.fieldWarningText.set("1st run");
    MyFieldSlowAnnotator.finished.set(false);
    MyFieldSlowAnnotator.stallMs.set(0);

    MyCommentFastAnnotator.finished.set(false);

    Map<com.intellij.lang.Language, MyRecordingAnnotator[]> annotatorsByLanguage = new HashMap<>();
    annotatorsByLanguage.put(JavaLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyFieldSlowAnnotator(), new MyCommentFastAnnotator()});
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);

    // both annos should produce their results
    useAnnotatorsIn(annotatorsByLanguage, () -> {
      List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
      assertTrue(infos.toString(), ContainerUtil.exists(infos, i -> i.getDescription().equals(MyFieldSlowAnnotator.fieldWarningText.get())));
      assertTrue(infos.toString(), ContainerUtil.exists(infos, i -> i.getDescription().equals(MyCommentFastAnnotator.fastToolText)));
      RangeHighlighter[] markers = model.getAllHighlighters();
      assertTrue(Arrays.toString(markers), ContainerUtil.exists(markers, i -> HighlightInfo.fromRangeHighlighter(i) != null && MyFieldSlowAnnotator.fieldWarningText.get().equals(HighlightInfo.fromRangeHighlighter(i).getDescription())));
      assertTrue(Arrays.toString(markers), ContainerUtil.exists(markers, i -> HighlightInfo.fromRangeHighlighter(i) != null && MyCommentFastAnnotator.fastToolText.equals(HighlightInfo.fromRangeHighlighter(i).getDescription())));
    });

    MyFieldSlowAnnotator.fieldWarningText.set("Aha, field, finally!");
    MyFieldSlowAnnotator.stallMs.set(100);
    // type another comment which will cause the warning about the first comment (by MyCommentFastAnnotator) to disappear
    // and check that as soon as MyCommentFastAnnotator is finished, it removed its own obsolete warnings, whereas MyFieldSlowAnnotator continues to run
    type("// another comment");
    MyCommentFastAnnotator.finished.set(false);
    MyFieldSlowAnnotator.finished.set(false);
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)myEditor); // get "visible area first" optimization out of the way
    useAnnotatorsIn(annotatorsByLanguage, () -> {
      // now when the highlighting is restarted, we should get back our inspection result very fast, despite very slow processing of every other element
      long deadline = System.currentTimeMillis() + 10_000;
      while (!DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        if (System.currentTimeMillis() > deadline) {
          fail("Too long waiting for daemon to start");
        }
      }
      try {
        boolean fastToolFinishedFaster = false;
        while (DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
          if (System.currentTimeMillis() > deadline) {
            fail("Too long waiting for daemon to finish\n" + ThreadDumper.dumpThreadsToString());
          }
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          if (MyCommentFastAnnotator.finished.get() && !MyFieldSlowAnnotator.finished.get()) {
            boolean fastToolWarningFound = !DaemonCodeAnalyzerEx.processHighlights(model, getProject(), HighlightSeverity.WARNING, 0,
                                                                    myEditor.getDocument().getTextLength(),
                                                                    info -> !MyCommentFastAnnotator.fastToolText.equals(info.getDescription()));
            fastToolFinishedFaster = true;
            if (fastToolWarningFound) {
              fail("Annotator must have removed its own obsolete highlights as soon as it's finished, but got:" +
                   StringUtil.join(model.getAllHighlighters(), Object::toString, "\n   ") + "; thread dump:\n" + ThreadDumper.dumpThreadsToString());
            }
          }
        }
        assertTrue("Fast inspection must have finished faster than the slow one, but it didn't", fastToolFinishedFaster);
      }
      finally {
        MyFieldSlowAnnotator.stallMs.set(0);
      }
    });
  }

  // highlight all "xxx" comments
  static class MyComment1Annotator extends MyRecordingAnnotator {
    static final AtomicBoolean stall1 = new AtomicBoolean();
    static final String comment1Text = "comment1Text";
    public MyComment1Annotator() {
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      while (stall1.get()) {
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
    static final AtomicBoolean stall2 = new AtomicBoolean();
    static final String comment2Text = "comment2Text";
    public MyComment2Annotator() {
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      while (stall2.get()) {
        Thread.yield();
        //ProgressManager.checkCanceled();
      }
      if (element instanceof PsiComment) {
        if (element.getText().contains("xxx")) {
          holder.newAnnotation(HighlightSeverity.WARNING, comment2Text).range(element).create();
          iDidIt();
          //stall2.set(true); // stall right after producing annotation
        }
      }
    }
  }

  public void testAnnotatorsMustNotWaitForEachOther() {
    @Language("JAVA")
    String text = """
      class LQF {
          // xxx
          int f;
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)myEditor); // get "visible area first" optimization out of the way
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()

    Map<com.intellij.lang.Language, MyRecordingAnnotator @NotNull []> annotatorsByLanguage = new HashMap<>();
    annotatorsByLanguage.put(JavaLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyComment1Annotator(), new MyComment2Annotator()});
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);

    // both annos should produce their results
    myDaemonCodeAnalyzer.restart();
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)myEditor); // get "visible area first" optimization out of the way
    useAnnotatorsIn(annotatorsByLanguage, () -> {
      long deadline = System.currentTimeMillis() + 20_000;
      while (!DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        if (System.currentTimeMillis() > deadline) {
          fail("Too long waiting for daemon to start");
        }
      }
      boolean tool1AnnoFound = false;
      boolean tool2AnnoFound = false;
      while (!tool1AnnoFound || !tool2AnnoFound) {
        if (System.currentTimeMillis() > deadline) {
          fail("Too long waiting for daemon to finish\n" + ThreadDumper.dumpThreadsToString());
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        tool1AnnoFound = !DaemonCodeAnalyzerEx.processHighlights(model, getProject(), HighlightSeverity.WARNING, 0,
                                                                 myEditor.getDocument().getTextLength(),
                                                                 info -> !MyComment1Annotator.comment1Text.equals(info.getDescription()));
        tool2AnnoFound = !DaemonCodeAnalyzerEx.processHighlights(model, getProject(), HighlightSeverity.WARNING, 0,
                                                                 myEditor.getDocument().getTextLength(),
                                                                 info -> !MyComment2Annotator.comment2Text.equals(info.getDescription()));
      }
      MyComment1Annotator.stall1.set(false);
      MyComment2Annotator.stall2.set(false);
      while (DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        if (System.currentTimeMillis() > deadline+1_000) {
          fail("Too long waiting for daemon to finish; stall1="+MyComment1Annotator.stall1+"; stall2="+MyComment2Annotator.stall2 +
               "\n" + ThreadDumper.dumpThreadsToString());
        }
        Thread.yield();
      }
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
  public void testCloseReopenDoesNotDuplicateWarnings() {
    useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyTextAnnotator()}, () -> {
      configureByText(PlainTextFileType.INSTANCE, "sssxxxsss");
      HighlightInfo info = assertOneElement(highlightErrors());
      assertEquals(MyTextAnnotator.SWEARING, info.getDescription());

      Document document = myFile.getFileDocument();
      VirtualFile virtualFile = myFile.getVirtualFile();
      FileEditorManager.getInstance(myProject).closeFile(virtualFile);
      UIUtil.dispatchAllInvocationEvents();

      configureByExistingFile(virtualFile);
      assertSame(document, getEditor().getDocument());
      HighlightInfo info2 = assertOneElement(highlightErrors());
      assertEquals(MyTextAnnotator.SWEARING, info2.getDescription());
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
  public void testFileLevelAnnotationDoesNotDuplicateOnTyping() {
    configureByText(PlainTextFileType.INSTANCE, "text<caret>");

    useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyFileLevelAnnotator()}, () -> {
      for (int i=0; i<100; i++) {
        assertEquals(MyFileLevelAnnotator.MSG, assertOneElement(highlightErrors()).getDescription());
        HighlightInfo info = assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
        assertEquals(MyFileLevelAnnotator.MSG, info.getDescription());

        type('2');
        assertEquals(MyFileLevelAnnotator.MSG, assertOneElement(highlightErrors()).getDescription());
        info = assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
        assertEquals(MyFileLevelAnnotator.MSG, info.getDescription());

        backspace();
      }
    });
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

  public void testFileLevelAnnotationDoesNotDuplicateOnTypingEventWhenItsDescriptionIsConstantlyChanging() {
    configureByText(PlainTextFileType.INSTANCE, "<caret>");

    useAnnotatorsIn(PlainTextLanguage.INSTANCE, new MyRecordingAnnotator[]{new MyFileLevelAnnotatorWithConstantlyChangingDescription()}, () -> {
      for (int i=0; i<100; i++) {
        assertTrue(MyFileLevelAnnotatorWithConstantlyChangingDescription.isMine(assertOneElement(highlightErrors())));
        HighlightInfo info = assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
        assertTrue(MyFileLevelAnnotatorWithConstantlyChangingDescription.isMine(info));

        type('2');
        assertTrue(MyFileLevelAnnotatorWithConstantlyChangingDescription.isMine(assertOneElement(highlightErrors())));
        info = assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
        assertTrue(MyFileLevelAnnotatorWithConstantlyChangingDescription.isMine(info));

        backspace();
      }
    });
  }
}
