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
import com.intellij.debugger.DebugException;
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
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ExceptionUtil;
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
  }

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
    ((EditorTrackerImpl)EditorTracker.getInstance(myProject)).setActiveEditors(Arrays.asList(editors));
  }

  @Override
  protected Sdk getTestProjectJdk() {
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
    //System.out.println("i = " + i);
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
        //System.out.println("i = " + i);
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
    }
  }

  public static class MyInfoAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && ((PsiComment)element).getTokenType().equals(JavaTokenType.C_STYLE_COMMENT)) {
        holder.newAnnotation(HighlightSeverity.INFORMATION, "comment").create();
        iDidIt();
      }
    }
  }

  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately() {
    PlatformTestUtil.assumeEnoughParallelism();
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyInfoAnnotator(), new MySleepyAnnotator(), new MyFastAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
  }
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately1() {
    PlatformTestUtil.assumeEnoughParallelism();
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MySleepyAnnotator(), new MyInfoAnnotator(), new MyFastAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
  }
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately2() {
    PlatformTestUtil.assumeEnoughParallelism();
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MySleepyAnnotator(), new MyFastAnnotator(), new MyInfoAnnotator(), }, this::checkSwearingHighlightIsVisibleImmediately);
  }
  public void testAddAnnotationToHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately3() {
    PlatformTestUtil.assumeEnoughParallelism();
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
    Runnable checkHighlighted = () -> {
      called.incrementAndGet();
      UIUtil.dispatchAllInvocationEvents();
      long highlighted = Arrays.stream(markupModel.getAllHighlighters())
        .map(highlighter -> HighlightInfo.fromRangeHighlighter(highlighter))
        .filter(Objects::nonNull)
        .filter(info -> MyFastAnnotator.SWEARING.equals(info.getDescription()))
        .count();
      if (highlighted != 0) {
        toSleepMs.set(0);
        throw new DebugException(); // sorry for that, had to differentiate from failure
      }
      if (n.timedOut()) {
        toSleepMs.set(0);
        throw new RuntimeException(new TimeoutException(ThreadDumper.dumpThreadsToString()));
      }
    };
    try {
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      long start = System.currentTimeMillis();
      myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), textEditor, ArrayUtilRt.EMPTY_INT_ARRAY, false, checkHighlighted);
      List<RangeHighlighter> errors = ContainerUtil.filter(markupModel.getAllHighlighters(), highlighter -> HighlightInfo.fromRangeHighlighter(highlighter) != null && HighlightInfo.fromRangeHighlighter(highlighter).getSeverity() == HighlightSeverity.ERROR);
      long elapsed = System.currentTimeMillis() - start;

      fail("should have been interrupted. toSleepMs: " + toSleepMs + "; highlights: " + errors + "; called: " + called+"; highlighted in "+elapsed+"ms");
    }
    catch (DebugException ignored) {
    }
  }

  public static class MyNewBuilderAnnotator extends MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        holder.newAnnotation(HighlightSeverity.ERROR, MyFastAnnotator.SWEARING).create();
        // sleep after creating annotation to emulate a very big annotator which does a great amount of work after registering annotation

        // use this contrived form to be able to bail out immediately by modifying toSleepMs in the other thread
        while (toSleepMs.addAndGet(-100) > 0) {
          TimeoutUtil.sleep(100);
        }
        iDidIt();
      }
    }
  }

  public void testAddAnnotationViaBuilderEntailsCreatingCorrespondingRangeHighlighterImmediately() {
    PlatformTestUtil.assumeEnoughParallelism();
    useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyRecordingAnnotator[]{new MyNewBuilderAnnotator()},
                    this::checkSwearingHighlightIsVisibleImmediately);
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
    PlatformTestUtil.assumeEnoughParallelism();

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
    AtomicReference<Throwable> reported = new AtomicReference<>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            new DaemonCodeAnalyzer.DaemonListener() {
              @Override
              public void daemonAnnotatorStatisticsGenerated(@NotNull AnnotationSession session,
                                                             @NotNull Collection<? extends AnnotatorStatistics> statistics,
                                                             @NotNull PsiFile file) {
                AnnotatorStatistics stat = assertOneElement(ContainerUtil.filter(statistics, stat1 -> stat1.annotator instanceof MyInfoAnnotator));
                Throwable old = reported.getAndSet(new Throwable());
                assertNull(old==null? null: ExceptionUtil.getMessage(old), old);
                assertEquals("Annotation(message='comment', severity='INFORMATION', toolTip='<html>comment</html>')", stat.firstAnnotation.toString());
                assertSame(stat.firstAnnotation, stat.lastAnnotation);
                assertTrue(stat.annotatorStartStamp > 0);
                assertTrue(stat.firstAnnotationStamp >= stat.annotatorStartStamp);
                assertTrue(stat.lastAnnotationStamp >= stat.firstAnnotationStamp);
                assertTrue(stat.annotatorFinishStamp >= stat.lastAnnotationStamp);
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
    assertNotNull(reported.get());
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
                                                "balh blah\n".repeat(1000));
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
                  "balh blah\n".repeat(1000);
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
}
