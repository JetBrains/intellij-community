// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspectionBase;
import com.intellij.codeInspection.miscGenerics.RawUseOfParameterizedTypeInspection;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.debugger.DebugException;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.editor.*;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.TestTimeOut;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
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
 * tests {@link LocalInspectionTool} behaviour during highlighting
 */
@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class DaemonInspectionsRespondToChangesTest extends DaemonAnalyzerTestCase {
  private DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
    myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    UndoManager.getInstance(myProject);
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
    PlatformTestUtil.assumeEnoughParallelism();
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
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    DaemonProgressIndicator.runInDebugMode(() -> super.runTestRunnable(testRunnable));
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
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {
      new FieldCanBeLocalInspection(),
      new RequiredAttributesInspectionBase(),
      new CheckDtdReferencesInspection(),
      new AccessStaticViaInstance(),
    };
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    // treat listeners added there as not leaks
    EditorMouseHoverPopupManager.getInstance();
  }

  public void testWholeFileInspection() throws Exception {
    configureByFile(DaemonRespondToChangesTest.BASE_PATH + "FieldCanBeLocal.java");
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertSize(1, infos);
    assertEquals("Field can be converted to a local variable", infos.get(0).getDescription());

    ctrlW();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> EditorModificationUtilEx.deleteSelectedText(getEditor()));
    type("xxxx");

    infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);

    ctrlW();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> EditorModificationUtilEx.deleteSelectedText(getEditor()));
    type("0");

    infos = doHighlighting(HighlightSeverity.WARNING);
    assertSize(1, infos);
    assertEquals("Field can be converted to a local variable", infos.get(0).getDescription());
  }

  private abstract static class MyTrackingInspection extends MyInspectionBase {
    private final List<PsiElement> visited = Collections.synchronizedList(new ArrayList<>());

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new PsiElementVisitor() {
        @Override
        public void visitFile(@NotNull PsiFile file) {
          TimeoutUtil.sleep(1000); // make it run longer than LIP
          super.visitFile(file);
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
          visited.add(element);
        }
      };
    }
  }

  static abstract class MyInspectionBase extends LocalInspectionTool {
    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
      return getClass().getName();
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public String getShortName() {
      return getGroupDisplayName();
    }
  }

  private static class MyWholeInspection extends MyTrackingInspection {
    @Override
    public boolean runForWholeFile() {
      return true;
    }
  }

  public void testWholeFileInspectionRestartedOnAllElements() {
    MyTrackingInspection tool = registerInspection(new MyWholeInspection());

    configureByText(JavaFileType.INSTANCE, "class X { void f() { <caret> } }");
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);
    int visitedCount = new HashSet<>(tool.visited).size();
    tool.visited.clear();

    type(" ");  // white space modification

    infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);

    int countAfter = new HashSet<>(tool.visited).size();
    assertTrue("visitedCount = "+visitedCount+"; countAfter="+countAfter, countAfter >= visitedCount);
  }

  private @NotNull <T extends LocalInspectionTool> T registerInspection(@NotNull T tool) {
    enableInspectionTool(tool);
    return tool;
  }

  public void testWholeFileInspectionRestartedEvenIfThereWasAModificationInsideCodeBlockInOtherFile() throws Exception {
    MyTrackingInspection tool = registerInspection(new MyWholeInspection());

    PsiFile file = configureByText(JavaFileType.INSTANCE, "class X { void f() { <caret> } }");
    PsiFile otherFile = createFile(myModule, file.getContainingDirectory().getVirtualFile(), "otherFile.txt", "xxx");
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);
    int visitedCount = tool.visited.size();
    assertTrue(tool.visited.toString(), visitedCount > 0);
    tool.visited.clear();

    Document otherDocument = Objects.requireNonNull(PsiDocumentManager.getInstance(getProject()).getDocument(otherFile));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> otherDocument.setText("zzz"));

    infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);

    int countAfter = tool.visited.size();
    assertTrue(tool.visited.toString(), countAfter > 0);
    tool.visited.clear();

    //ensure started on another file
    configureByExistingFile(otherFile.getVirtualFile());
    infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);

    int countAfter2 = tool.visited.size();
    assertTrue(tool.visited.toString(), countAfter2 > 0);
  }

  public void testInspectionIsRestartedOnPsiCacheDrop() {
    MyTrackingInspection tool = registerInspection(new MyTrackingInspection(){});

    configureByText(JavaFileType.INSTANCE, "class X { void f() { <caret> } }");
    DaemonRespondToChangesTest.waitForDaemon(myProject, myEditor.getDocument());
    tool.visited.clear();

    getPsiManager().dropPsiCaches();

    DaemonRespondToChangesTest.waitForDaemon(myProject, myEditor.getDocument());
    assertNotEmpty(tool.visited);
  }

  public void testLIPGetAllParentsAfterCodeBlockModification() {
    @Language("JAVA")
    String text = """
      class LQF {
          int f;
          public void me() {
              //<caret>
          }
      }""";
    configureByText(JavaFileType.INSTANCE, text);

    List<PsiElement> visitedElements = Collections.synchronizedList(new ArrayList<>());
    class MyVisitor extends PsiElementVisitor {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        visitedElements.add(element);
      }
    }
    LocalInspectionTool tool = new MyInspectionBase() {
      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new MyVisitor();
      }
    };
    disposeOnTearDown(() -> disableInspectionTool(tool.getShortName()));
    enableInspectionTool(tool);

    assertEmpty(highlightErrors());

    List<PsiElement> allPsi = CollectHighlightsUtil.getElementsInRange(myFile, 0, myFile.getTextLength());
    assertEquals(new HashSet<>(allPsi), new HashSet<>(visitedElements));

    // inside code block modification
    visitedElements.clear();
    backspace();
    backspace();

    assertEmpty(highlightErrors());

    PsiMethod method = ((PsiJavaFile)myFile).getClasses()[0].getMethods()[0];
    List<PsiElement> methodAndParents =
      CollectHighlightsUtil.getElementsInRange(myFile, method.getTextRange().getStartOffset(), method.getTextRange().getEndOffset(), true);
    assertEquals(new HashSet<>(methodAndParents), new HashSet<>(visitedElements));
  }

  public void testDumbQuickFixIsNoLongerVisibleAfterApplied() {
    registerInspection(new FindElseBranchInspection());

    @Language("JAVA")
    String text = "class X { void f() { if (this == null) {} else return; } }";
    configureByText(JavaFileType.INSTANCE, text);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().setText(text));
    getEditor().getCaretModel().moveToOffset(getFile().getText().indexOf("if (") + 1);
    assertEmpty(doHighlighting(HighlightSeverity.ERROR));
    List<IntentionAction> fixes = findStupidFixes();
    IntentionAction fix = assertOneElement(fixes);
    fix.invoke(getProject(), getEditor(), getFile());

    fixes = findStupidFixes();
    assertEmpty(fixes);

    assertEmpty(doHighlighting(HighlightSeverity.ERROR));
    fixes = findStupidFixes();
    assertEmpty(fixes);
  }

  private @NotNull List<IntentionAction> findStupidFixes() {
    return ContainerUtil.filter(CodeInsightTestFixtureImpl.getAvailableIntentions(getEditor(), getFile()), f -> f.getFamilyName()
      .equals(new FindElseBranchInspection.StupidQuickFixWhichDoesntCheckItsOwnApplicability().getFamilyName()));
  }

  private static class FindElseBranchInspection extends LocalInspectionTool {
    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
      return "danuna";
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public String getShortName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitIfStatement(@NotNull PsiIfStatement statement) {
          if (statement.getElseBranch() != null) {
            PsiKeyword keyword = (PsiKeyword)statement.getChildren()[0];
            holder.registerProblem(keyword, "Dododo", new StupidQuickFixWhichDoesntCheckItsOwnApplicability());
          }
        }
      };
    }
    private static class StupidQuickFixWhichDoesntCheckItsOwnApplicability implements LocalQuickFix {
      @Nls
      @NotNull
      @Override
      public String getName() {
        return "danu";
      }

      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return getName();
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        WriteCommandAction.runWriteCommandAction(project, () -> ((PsiIfStatement)descriptor.getPsiElement().getParent()).getElseBranch().delete());
      }
    }
  }

  private static final AtomicInteger toSleepMs = new AtomicInteger(0);
  private static final String SWEARING = "No swearing";

  private void checkSwearingHighlightIsVisibleImmediately() throws Exception {
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
        .filter(info -> SWEARING.equals(info.getDescription()))
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

  public void testAddInspectionProblemToProblemHolderEntailsCreatingCorrespondingRangeHighlighterMoreOrLessImmediately() throws Exception {
    registerInspection(new MySwearingInspection());
    checkSwearingHighlightIsVisibleImmediately();
  }

  // produces an error problem for "XXX" comment very fast, and then very slowly inspects the rest of the file
  private static class MySwearingInspection extends LocalInspectionTool {
    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
      return "sweardanuna";
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public String getShortName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitComment(@NotNull PsiComment element) {
          if (element.getText().equals("//XXX")) {
            holder.registerProblem(element, SWEARING);
          }
        }

        @Override
        public void visitFile(@NotNull PsiFile file) {
          // use this contrived form to be able to bail out immediately by modifying toSleepMs in the other thread
          while (toSleepMs.addAndGet(-100) > 0) {
            TimeoutUtil.sleep(100);
          }
        }
      };
    }
  }

  public void testLocalInspectionPassMustRunFastOrFertileInspectionsFirstToReduceLatency() {
    @Language("JAVA")
    String text = """
      class LQF {
          int f;
          public void me() {
              //
          }
       void foo//<caret>
      (){}}""";
    configureByText(JavaFileType.INSTANCE, text);
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)myEditor); // get "visible area first" optimization out of the way
    UIUtil.markAsFocused(getEditor().getContentComponent(), true); // to make ShowIntentionPass call its collectInformation()
    SeverityRegistrar.getSeverityRegistrar(getProject()); //preload inspection profile

    AtomicReference<String> diagnosticText = new AtomicReference<>("1st run");
    AtomicInteger stallMs = new AtomicInteger();
    // highlight fields, stall every other element
    LocalInspectionTool tool = new MyInspectionBase() {
      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
          @Override
          public void visitField(@NotNull PsiField field) {
            holder.registerProblem(field.getNameIdentifier(), diagnosticText.get());
          }

          @Override
          public void visitElement(@NotNull PsiElement element) {
            // stall every other element to exacerbate latency problems if the order is wrong
            TimeoutUtil.sleep(stallMs.get());
          }
        };
      }
    };
    disposeOnTearDown(() -> disableInspectionTool(tool.getShortName()));
    for (Tools tools : ProjectInspectionProfileManager.getInstance(getProject()).getCurrentProfile().getAllEnabledInspectionTools(getProject())) {
      disableInspectionTool(tools.getTool().getShortName());
    }
    enableInspectionTool(tool);

    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    HighlightInfo i = assertOneElement(infos);
    assertEquals(diagnosticText.get(), i.getDescription());

    diagnosticText.set("Aha, field, finally!");
    stallMs.set(10000);
    backspace();
    backspace();
    type("blah");
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)myEditor); // get "visible area first" optimization out of the way

    // now when the LIP restarted, we should get back our inspection result very fast, despite very slow processing of every other element
    long deadline = System.currentTimeMillis() + 10_000;
    while (!DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      if (System.currentTimeMillis() > deadline) {
        fail("Too long waiting for daemon to start");
      }
    }
    PsiField field = ((PsiJavaFile)getFile()).getClasses()[0].getFields()[0];
    TextRange range = field.getNameIdentifier().getTextRange();
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);
    while (DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
      if (System.currentTimeMillis() > deadline) {
        DaemonRespondToChangesPerformanceTest.dumpThreadsToConsole();
        fail("Too long waiting for daemon to finish");
      }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      boolean found = !DaemonCodeAnalyzerEx.processHighlights(model, getProject(), HighlightSeverity.WARNING, range.getStartOffset(), range.getEndOffset(), info -> !diagnosticText.get().equals(info.getDescription()));
      if (found) {
        break;
      }
    }
  }

  private static volatile TextRange expectedVisibleRange;
  private static class MyVisibleRangeInspection extends MyTrackingInspection {
    @Override
    public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {
      TextRange priorityRange = session.getPriorityRange();
      assertEquals(expectedVisibleRange, priorityRange);
      expectedVisibleRange = null;
    }
  }

  private static final String wordToAnnotate = "annotate_here";
  public void testLocalInspectionMustReceiveCorrectVisibleRangeViaItsHighlightingSession() {
    registerInspection(new MyVisibleRangeInspection());

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
    myDaemonCodeAnalyzer.restart(getTestName(false));
    expectedVisibleRange = visibleRange;
    doHighlighting();
    assertNull(expectedVisibleRange); // check the inspection was run
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible(editor);
    myDaemonCodeAnalyzer.restart(getTestName(false));
    expectedVisibleRange = new TextRange(0, editor.getDocument().getTextLength());
    doHighlighting();
    assertNull(expectedVisibleRange); // check the inspection was run
  }

  // add file-level "blah" for each identifier containing "XXX"
  private static class MyFileLevelInspection extends MyInspectionBase {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitIdentifier(@NotNull PsiIdentifier identifier) {
          if (identifier.getText().contains("XXX")) {
            holder.registerProblem(identifier.getContainingFile(),"blah", ProblemHighlightType.WARNING);
          }
        }
      };
    }
  }
  public void testFileLevelHighlightingDoesNotDuplicateOnTypingInsideSmallRange() {
    registerInspection(new MyFileLevelInspection());
    @Language("JAVA")
    String text = """
      class X {
        void foo() {
          int XXX<caret>;
        }
      }""";
    configureByText(JavaFileType.INSTANCE, text);

    assertEmpty(highlightErrors());
    assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));

    type('2');
    assertEmpty(highlightErrors());
    assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
  }

  public void testFileLevelHighlightingDoesNotDuplicateOnTypingOrOnFileCloseReopen() {
    registerInspection(new MyFileLevelInspection());
    @Language("JAVA")
    String text = """
      class X {
        int XXX<caret>;
      }""";
    PsiFile psiFile = configureByText(JavaFileType.INSTANCE, text);

    assertEmpty(highlightErrors());
    assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
    FileEditorManager.getInstance(myProject).closeFile(psiFile.getVirtualFile());

    for (int i=0; i<100; i++) {
      configureByExistingFile(psiFile.getVirtualFile());
      getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf("XXX") + "XXX".length());

      UIUtil.dispatchAllInvocationEvents();
      type('2');
      assertEmpty(highlightErrors());
      UIUtil.dispatchAllInvocationEvents();
      assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
      UIUtil.dispatchAllInvocationEvents();
      backspace();
      assertEmpty(highlightErrors());
      UIUtil.dispatchAllInvocationEvents();
      assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
      
      LOG.debug("i = " + i);

      FileEditorManager.getInstance(myProject).closeFile(psiFile.getVirtualFile());
    }
  }

  public void testFileLevelWithEverChangingDescriptionMustUpdateOnTyping() {
    // add file-level "blah" if there are identifiers containing "xxx"
    class XXXIdentifierFileLevelInspection extends MyInspectionBase {
      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
          @Override
          public void visitIdentifier(@NotNull PsiIdentifier identifier) {
            if (identifier.getText().contains("xxx")) {
              holder.registerProblem(identifier.getContainingFile(),"xxx: "+identifier.getText(), ProblemHighlightType.WARNING);
            }
          }
        };
      }
    }

    registerInspection(new XXXIdentifierFileLevelInspection());
    @Language("JAVA")
    String text = """
      class X {
        void foo() {
          int xxx<caret>;
        }
      }""";
    configureByText(JavaFileType.INSTANCE, text);

    assertEmpty(highlightErrors());
    HighlightInfo info = assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
    assertEquals("xxx: xxx", info.getDescription());

    type('2');
    assertEmpty(highlightErrors());
    info = assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
    assertEquals("xxx: xxx2", info.getDescription());

    type('y');
    assertEmpty(highlightErrors());
    info = assertOneElement(myDaemonCodeAnalyzer.getFileLevelHighlights(getProject(), getFile()));
    assertEquals("xxx: xxx2y", info.getDescription());
  }

  public void testInspectionMustRemoveItsObsoleteHighlightsImmediatelyAfterFinished() {
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

    AtomicReference<String> fieldWarningText = new AtomicReference<>("1st run");
    AtomicInteger stallMs = new AtomicInteger(0);
    AtomicBoolean slowToolFinished = new AtomicBoolean();
    LocalInspectionTool slowTool = new MyInspectionBase() {
      @Override
      public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
        // invoke later because we are checking this flag in EDT below, and
        // we do not want a race between contextFinishedCallback.accept(context); in inspection thread
        // and querying markup model in EDT
        ApplicationManager.getApplication().invokeLater(() -> {
          //System.out.println("slow finished ");
          slowToolFinished.set(true);
        });
      }
      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
          @Override
          public void visitField(@NotNull PsiField field) {
            //System.out.println("slow visit field "+field + Thread.currentThread());
            holder.registerProblem(field.getNameIdentifier(), fieldWarningText.get());
          }

          @Override
          public void visitElement(@NotNull PsiElement element) {
            //System.out.println("slow visit "+element + Thread.currentThread());
            // stall every other element to exacerbate latency problems if the order is wrong
            TimeoutUtil.sleep(stallMs.get());
          }
        };
      }
    };

    AtomicBoolean fastToolFinished = new AtomicBoolean();
    // highlights all "xxx" comments, only when there are no comments after it
    String fastToolText = "blah";
    LocalInspectionTool fastTool = new MyInspectionBase() {
      @Override
      public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
        // invoke later because we are checking this flag in EDT below, and
        // we do not want a race between contextFinishedCallback.accept(context); in inspection thread
        // and querying markup model in EDT
        //System.out.println("fast about to finished ");
        ApplicationManager.getApplication().invokeLater(() -> {
          //System.out.println("fast finished ");
          fastToolFinished.set(true);
        });
      }
      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
          @Override
          public void visitComment(@NotNull PsiComment comment) {
            //System.out.println("fast visit comment "+comment + Thread.currentThread());
            if (comment.getText().contains("xxx") && !comment.getContainingFile().getText().substring(comment.getTextOffset()+2).contains("//")) {
              holder.registerProblem(comment, fastToolText, ProblemHighlightType.WARNING);
            }
          }

          @Override
          public void visitElement(@NotNull PsiElement element) {
            //System.out.println("fast visit "+element + Thread.currentThread());
          }
        };
      }
    };
    disposeOnTearDown(() -> disableInspectionTool(fastTool.getShortName()));
    disposeOnTearDown(() -> disableInspectionTool(slowTool.getShortName()));
    for (Tools tools : ProjectInspectionProfileManager.getInstance(getProject()).getCurrentProfile().getAllEnabledInspectionTools(getProject())) {
      disableInspectionTool(tools.getTool().getShortName());
    }
    enableInspectionTools(fastTool, slowTool);

    // both inspections should produce their results
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertTrue(infos.toString(), ContainerUtil.exists(infos, i -> i.getDescription().equals(fieldWarningText.get())));
    assertTrue(infos.toString(), ContainerUtil.exists(infos, i -> i.getDescription().equals(fastToolText)));

    fieldWarningText.set("Aha, field, finally!");
    stallMs.set(100);
    type("// another comment");
    //System.out.println("-------------");
    fastToolFinished.set(false);
    slowToolFinished.set(false);
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)myEditor); // get "visible area first" optimization out of the way

    // now when the LIP restarted, we should get back our inspection result very fast, despite very slow processing of every other element
    long deadline = System.currentTimeMillis() + 10_000;
    while (!DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      if (System.currentTimeMillis() > deadline) {
        fail("Too long waiting for daemon to start");
      }
    }
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);
    try {
      boolean fastToolFinishedFaster = false;
      while (DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
        if (System.currentTimeMillis() > deadline) {
          fail("Too long waiting for daemon to finish\n" + ThreadDumper.dumpThreadsToString());
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        if (fastToolFinished.get() && !slowToolFinished.get()) {
          fastToolFinishedFaster = true;
          boolean fastFound = !DaemonCodeAnalyzerEx.processHighlights(model, getProject(), HighlightSeverity.WARNING, 0, myEditor.getDocument().getTextLength(),
                          info -> !fastToolText.equals(info.getDescription()));
          if (fastFound) {
            fail("Inspection must have removed its own obsolete highlights as soon as it's finished, but got:" +
                 StringUtil.join(model.getAllHighlighters(), Object::toString, "\n   ")+"; thread dump:\n"+ThreadDumper.dumpThreadsToString());
          }
        }
      }
      assertTrue("Fast inspection must have finished faster than the slow one, but it didn't", fastToolFinishedFaster);
    }
    finally {
      stallMs.set(0);
    }
  }

  public void testModificationInsideCommentDoesNotAffectNearbyInspectionWarning() {
    enableInspectionTool(new ConstantValueInspection());
    @Language("JAVA")
    String text = """
        class AClass {
          public int foo() {
            //<caret>
            if (this == null) return 0;
            return 1;
          }
        }
      """;
    configureByText(JavaFileType.INSTANCE, text);

    assertEmpty(highlightErrors());
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    HighlightInfo error = ContainerUtil.find(infos, e->e.getDescription().contains("always 'false'"));
    assertNotNull(infos.toString(), error);
    type("d");
    List<HighlightInfo> infos2 = doHighlighting(HighlightSeverity.WARNING);
    HighlightInfo error2 = ContainerUtil.find(infos2, e->e.getDescription().contains("always 'false'"));
    assertNotNull(infos2.toString(), error2);
  }

  private static class MyException extends RuntimeException {
    MyException() {
      super("MyPreciousException");
    }
  }

  public void testThrowingExceptionFromInspectionMustPropagateUpToTheLogger() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    MyInspectionBase throwExceptionInspection = new MyInspectionBase() {
      @Override
      public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
          @Override
          public void visitFile(@NotNull PsiFile file) {
            throw new MyException();
          }
        };
      }
    };
    enableInspectionTool(throwExceptionInspection);
    @Language("JAVA")
    String text = """
        class AClass {
        }
      """;
    configureByText(JavaFileType.INSTANCE, text);

    assertThrows(Throwable.class, new MyException().getMessage(), () -> highlightErrors());
  }

  public void testInspectionMustRemoveItsObsoleteHighlightsImmediatelyAfterVisitingPSIElementTheSecondTimeAndFailingToGenerateTheSameWarningAgain() {
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

    String fieldWarningText = "1st run";
    AtomicBoolean fieldIdentifierVisited = new AtomicBoolean();
    AtomicBoolean fieldHighlightsUpdated = new AtomicBoolean();
    AtomicBoolean fieldToolMustWarn = new AtomicBoolean(true);
    // highlight each field on visitField() if said so, stall on every other element
    LocalInspectionTool fieldTool = new MyInspectionBase() {
      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
          @Override
          public void visitIdentifier(@NotNull PsiIdentifier identifier) {
            if (identifier.getParent() instanceof PsiField) {
              if (fieldToolMustWarn.get()) {
                holder.registerProblem(identifier, fieldWarningText);
              }
              fieldIdentifierVisited.set(true);
            }
          }

          @Override
          public void visitElement(@NotNull PsiElement element) {
            if (fieldIdentifierVisited.get()) {
              // after visitIdentifier() has completed, before the next visit() method is called, the highlights must be updated
              fieldHighlightsUpdated.set(true);
            }
          }
        };
      }
    };

    enableInspectionTools(fieldTool);

    // inspections should produce their results
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertTrue(infos.toString(), ContainerUtil.exists(infos, i -> i.getDescription().equals(fieldWarningText)));
    assertTrue(fieldIdentifierVisited.get());
    assertTrue(fieldHighlightsUpdated.get());

    fieldIdentifierVisited.set(false);
    fieldHighlightsUpdated.set(false);
    fieldToolMustWarn.set(false);
    type("// another comment\nvoid anotherMethod(){}");
    DaemonRespondToChangesTest.makeWholeEditorWindowVisible((EditorImpl)myEditor); // get "visible area first" optimization out of the way

    long deadline = System.currentTimeMillis() + 10_000;
    while (!DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      if (System.currentTimeMillis() > deadline) {
        fail("Too long waiting for daemon to start");
      }
    }
    // now when the LIP restarted, we should observe the range highlighter for the inspection to disappear as soon as visitIdentifier() method is finished
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);
    while (DaemonRespondToChangesTest.daemonIsWorkingOrPending(myProject, myEditor.getDocument())) {
      if (System.currentTimeMillis() > deadline) {
        fail("Too long waiting for daemon to finish\n"+ThreadDumper.dumpThreadsToString());
      }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      if (fieldHighlightsUpdated.get()) {
        boolean found = !DaemonCodeAnalyzerEx.processHighlights(model, getProject(), HighlightSeverity.WARNING, 0, myEditor.getDocument().getTextLength(),
                        info -> !fieldWarningText.equals(info.getDescription()));
        if (found) {
          fail("Inspection must have its obsolete highlights removed as soon as its visitIdentifier() is finished, but got:" +
               StringUtil.join(model.getAllHighlighters(), Object::toString, "\n   ") + "; thread dump:\n" + ThreadDumper.dumpThreadsToString());
        }
      }
    }
    assertTrue(fieldIdentifierVisited.get());
    assertTrue(fieldHighlightsUpdated.get());
  }

  public void testRedundantWarningMustNotBlinkOnTypingInsideParameterModifierList() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    enableInspectionTools(new RedundantSuppressInspection(), new RawUseOfParameterizedTypeInspection());
    @Language("JAVA")
    String text = """
     abstract class Converter<T> {
       abstract T fromString(String value);
     }
     class Base {
      protected Base(String accessor,
                     /*caret goes here*/Class<? extends Converter> converter) {
        System.out.println(accessor + converter);
      }
     }
     """;
    configureByText(JavaFileType.INSTANCE, text);
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertTrue(infos.toString(), ContainerUtil.exists(infos, info-> new RawUseOfParameterizedTypeInspection().getShortName()
      .equals(info.getInspectionToolId())));
    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf("Class<?"));
    type("@SuppressWarnings(\"rawtypes\")    ") ;
    assertEmpty(doHighlighting(HighlightSeverity.WARNING));
    type("\n");
    assertEmpty(doHighlighting(HighlightSeverity.WARNING));
  }

  public void testHighlightsForInvalidPSIMustBeRemovedFastForExampleBeforeTheInspectionsStartRunning() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    LocalInspectionTool myTool = new MyInspectionBase() {
      @Override
      public @NotNull String getShortName() {
        return "my own tool xxx";
      }

      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
          @Override
          public void visitIdentifier(@NotNull PsiIdentifier identifier) {
            if (identifier.getText().contains("xxx")) {
              // by this moment all highlights for invalid PSI must be removed

              List<RangeHighlighter> highlighters = List.of(DocumentMarkupModel.forDocument(getDocument(myFile), myProject, true).getAllHighlighters());
              assertFalse(ContainerUtil.exists(highlighters, r -> HighlightInfo.fromRangeHighlighter(r) != null && getShortName().equals(HighlightInfo.fromRangeHighlighter(r).getInspectionToolId())));

              holder.registerProblem(identifier, "XXX", ProblemHighlightType.WARNING);
            }
          }
        };
      }
    };

    enableInspectionTools(myTool);

    @Language("JAVA")
    String text = """
     class Base {
       int xxx;
     }
     """;
    configureByText(JavaFileType.INSTANCE, text);
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertOneElement(ContainerUtil.filter(infos, info-> myTool.getShortName().equals(info.getInspectionToolId())));
    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf("int xxx;"));
    type("// ") ;
    assertEmpty(doHighlighting(HighlightSeverity.WARNING));
  }
}
