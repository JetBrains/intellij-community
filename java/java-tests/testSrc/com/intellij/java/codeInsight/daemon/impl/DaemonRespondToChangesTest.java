// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.application.UtilKt;
import com.intellij.application.options.editor.CodeFoldingConfigurable;
import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.EditorInfo;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspectionBase;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.lang.*;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.inline.InlineRefactoringActionHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import gnu.trove.THashSet;
import kotlin.Unit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author cdr
 */
@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
@SkipSlowTestLocally
public class DaemonRespondToChangesTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typing/";

  private DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
    myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    UndoManager.getInstance(myProject);
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
    DaemonProgressIndicator.setDebug(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Project project = getProject();
      if (project != null) {
        doPostponedFormatting(project);
        ((ProjectManagerImpl)ProjectManagerEx.getInstanceEx()).forceCloseProject(project, false);
      }
    }
    finally {
      myDaemonCodeAnalyzer = null;
      super.tearDown();
    }
  }

  @Override
  protected boolean doTestLineMarkers() {
    return true;
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    ProjectManagerEx.getInstanceEx().openProject(getProject());
    super.runStartupActivities();
    UIUtil.dispatchAllInvocationEvents(); // startup activities
  }

  @Override
  protected void runStartupActivities() {
  }

  private static void typeInAlienEditor(Editor alienEditor, char c) {
    TypedAction action = EditorActionManager.getInstance().getTypedAction();
    DataContext dataContext = ((EditorEx)alienEditor).getDataContext();

    action.actionPerformed(alienEditor, c, dataContext);
  }

  public void testHighlightersUpdate() throws Exception {
    configureByFile(BASE_PATH + "HighlightersUpdate.java");
    Document document = getDocument(getFile());
    highlightErrors();
    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.ERROR, getProject());
    assertEquals(1, errors.size());
    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, Pass.UPDATE_ALL);
    assertNull(dirty);

    type(' ');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, Pass.UPDATE_ALL);
    assertNotNull(dirty);
  }


  public void testNoPsiEventsAltogether() throws Exception {
    configureByFile(BASE_PATH + "HighlightersUpdate.java");
    Document document = getDocument(getFile());
    highlightErrors();
    type(' ');
    backspace();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, Pass.UPDATE_ALL);
    assertEquals(getFile().getTextRange(), dirty); // have to rehighlight whole file in case no PSI events have come
  }

  public void testRenameClass() throws Exception {
    configureByFile(BASE_PATH + "AClass.java");
    Document document = getDocument(getFile());
    Collection<HighlightInfo> infos = highlightErrors();
    assertEquals(0, infos.size());
    final PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];
    new RenameProcessor(myProject, psiClass, "Class2", false, false).run();
    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, Pass.UPDATE_ALL);
    assertEquals(getFile().getTextRange(), dirty);

    highlightErrors();
    assertTrue(myDaemonCodeAnalyzer.isErrorAnalyzingFinished(getFile()));
  }


  public void testTypingSpace() throws Exception {
    configureByFile(BASE_PATH + "AClass.java");
    Document document = getDocument(getFile());
    Collection<HighlightInfo> infos = highlightErrors();
    assertEquals(0, infos.size());

    type("  ");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement elementAtCaret = myFile.findElementAt(myEditor.getCaretModel().getOffset());
    assertTrue(elementAtCaret instanceof PsiWhiteSpace);

    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, Pass.UPDATE_ALL);
    assertEquals(elementAtCaret.getTextRange(), dirty);
    highlightErrors();
    assertTrue(myDaemonCodeAnalyzer.isErrorAnalyzingFinished(getFile()));
  }


  public void testTypingSpaceInsideError() throws Exception {
    configureByFile(BASE_PATH + "Error.java");
    Collection<HighlightInfo> infos = highlightErrors();
    assertEquals(1, infos.size());

    for (int i = 0; i < 100; i++) {
      type(" ");
      List<HighlightInfo> errors = highlightErrors();
      assertEquals(1, errors.size());
    }
  }


  public void testBackSpaceInsideError() throws Exception {
    configureByFile(BASE_PATH + "BackError.java");
    Collection<HighlightInfo> infos = highlightErrors();
    assertEquals(1, infos.size());

    backspace();
    List<HighlightInfo> errors = highlightErrors();
    assertEquals(1, errors.size());
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    if (isStressTest() && !getTestName(false).equals("TypingCurliesClearsEndOfFileErrorsInPhp_ItIsPerformanceTestBecauseItRunsTooLong")) {
      // all possible inspections
      List<InspectionToolWrapper> all = InspectionToolRegistrar.getInstance().createTools();
      List<LocalInspectionTool> locals = new ArrayList<>();
      all.stream().filter(tool -> tool instanceof LocalInspectionToolWrapper).forEach(tool -> {
        LocalInspectionTool e = ((LocalInspectionToolWrapper)tool).getTool();
        locals.add(e);
      });
      return locals.toArray(LocalInspectionTool.EMPTY_ARRAY);
    }
    return new LocalInspectionTool[]{
      new FieldCanBeLocalInspection(),
      new RequiredAttributesInspectionBase(),
      new CheckDtdReferencesInspection(),
      new AccessStaticViaInstance(),
    };
  }


  public void testUnusedFieldUpdate() throws Exception {
    configureByFile(BASE_PATH + "UnusedField.java");
    Document document = getDocument(getFile());
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(1, infos.size());
    assertEquals("Private field 'ffff' is never used", infos.get(0).getDescription());

    type("  foo(ffff++);");
    highlightErrors();

    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, getProject());
    assertEquals(0, errors.size());
  }

  public void testUnusedMethodUpdate() {
    configureByText(JavaFileType.INSTANCE, "class X {\n" +
                                           "    static void ffff() {}\n" +
                                           "    public static void main(String[] args){\n" +
                                           "        for (int i=0; i<1000;i++) {\n" +
                                           "            System.out.println(i);\n" +
                                           "            <caret>ffff();\n" +
                                           "        }\n" +
                                           "    }\n}");
    enableInspectionTool(new UnusedDeclarationInspection(true));
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);

    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE);
    infos = doHighlighting(HighlightSeverity.WARNING);

    assertEquals(1, infos.size());
    assertEquals("Method 'ffff()' is never used", infos.get(0).getDescription());
  }


  public void testAssignedButUnreadFieldUpdate() throws Exception {
    configureByFile(BASE_PATH + "AssignedButUnreadField.java");
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(1, infos.size());
    assertEquals("Private field 'text' is assigned but never accessed", infos.get(0).getDescription());

    ctrlW();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> EditorModificationUtil.deleteSelectedText(getEditor()));
    type("  text");

    List<HighlightInfo> errors = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(getFile().getText(), errors);
  }

  public void testDaemonIgnoresNonPhysicalEditor() throws Exception {
    configureByFile(BASE_PATH + "AClass.java");
    highlightErrors();

    EditorFactory editorFactory = EditorFactory.getInstance();
    final Document consoleDoc = editorFactory.createDocument("my console blah");
    final Editor consoleEditor = editorFactory.createEditor(consoleDoc);

    try {
      checkDaemonReaction(false, () -> caretRight(consoleEditor));
      checkDaemonReaction(true, () -> typeInAlienEditor(consoleEditor, 'x'));
      checkDaemonReaction(true, () -> LightPlatformCodeInsightTestCase.backspace(consoleEditor, getProject()));

      //real editor
      checkDaemonReaction(true, this::caretRight);
    }
    finally {
      editorFactory.releaseEditor(consoleEditor);
    }
  }


  public void testDaemonIgnoresConsoleActivities() throws Exception {
    configureByFile(BASE_PATH + "AClass.java");
    doHighlighting(HighlightSeverity.WARNING);

    final ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(getProject()).getConsole();

    consoleView.getComponent(); //create editor
    consoleView.print("haha", ConsoleViewContentType.NORMAL_OUTPUT);
    UIUtil.dispatchAllInvocationEvents();

    try {
      checkDaemonReaction(false, () -> {
        consoleView.clear();
        try {
          Thread.sleep(300); // *&^ing alarm
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        UIUtil.dispatchAllInvocationEvents(); //flush
      });
      checkDaemonReaction(false, () -> {
        consoleView.print("sss", ConsoleViewContentType.NORMAL_OUTPUT);
        try {
          Thread.sleep(300); // *&^ing alarm
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        UIUtil.dispatchAllInvocationEvents(); //flush
      });
      checkDaemonReaction(false, () -> {
        consoleView.setOutputPaused(true);
        try {
          Thread.sleep(300); // *&^ing alarm
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        UIUtil.dispatchAllInvocationEvents(); //flush
      });
    }
    finally {
      Disposer.dispose(consoleView);
    }
  }

  private void checkDaemonReaction(boolean mustCancelItself, @NotNull final Runnable action) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    highlightErrors();
    myDaemonCodeAnalyzer.waitForTermination();
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());

    final AtomicBoolean run = new AtomicBoolean();
    Disposable disposable = Disposer.newDisposable();
    final AtomicReference<RuntimeException> stopDaemonReason = new AtomicReference<>();
    StorageUtilKt.setDEBUG_LOG("");
    getProject().getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            new DaemonCodeAnalyzer.DaemonListenerAdapter() {
              @Override
              public void daemonCancelEventOccurred(@NotNull String reason) {
                RuntimeException e = new RuntimeException("Some bastard's restarted daemon: " + reason +
                                                          "\nStorage write log: ----------\n" +
                                                          StorageUtilKt.getDEBUG_LOG() + "\n--------------");
                stopDaemonReason.compareAndSet(null, e);
              }
            });
    try {
      while (true) {
        try {
          int[] toIgnore = new int[0];
          Runnable callbackWhileWaiting = () -> {
            if (!run.getAndSet(true)) {
              action.run();
            }
          };
          myDaemonCodeAnalyzer
            .runPasses(getFile(), getDocument(getFile()), Collections.singletonList(textEditor), toIgnore, true, callbackWhileWaiting);
          break;
        }
        catch (ProcessCanceledException ignored) { }
      }

      if (mustCancelItself) {
        assertNotNull(stopDaemonReason.get());
      }
      else {
        if (stopDaemonReason.get() != null) throw stopDaemonReason.get();
      }
    }
    finally {
      StorageUtilKt.setDEBUG_LOG(null);
      Disposer.dispose(disposable);
    }
  }


  public void testWholeFileInspection() throws Exception {
    configureByFile(BASE_PATH + "FieldCanBeLocal.java");
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(1, infos.size());
    assertEquals("Field can be converted to a local variable", infos.get(0).getDescription());

    ctrlW();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> EditorModificationUtil.deleteSelectedText(getEditor()));
    type("xxxx");

    infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);

    ctrlW();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> EditorModificationUtil.deleteSelectedText(getEditor()));
    type("0");

    infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(1, infos.size());
    assertEquals("Field can be converted to a local variable", infos.get(0).getDescription());
  }

  private static class MyTrackingInspection extends LocalInspectionTool {
    private final List<PsiElement> visited = Collections.synchronizedList(new ArrayList<>());

    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
      return "fegna";
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
      return new PsiElementVisitor() {
        @Override
        public void visitFile(PsiFile file) {
          TimeoutUtil.sleep(1000); // make it run longer that LIP
          super.visitFile(file);
        }

        @Override
        public void visitElement(PsiElement element) {
          visited.add(element);
          super.visitElement(element);
        }
      };
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

  @NotNull
  private <T extends LocalInspectionTool> T registerInspection(T tool) {
    enableInspectionTool(tool);
    disposeOnTearDown(() -> disableInspectionTool(tool.getShortName()));
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

    Document otherDocument = ObjectUtils.assertNotNull(PsiDocumentManager.getInstance(getProject()).getDocument(otherFile));
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

  public void testDaemonIsRestartedOnPsiCacheDrop() {
    MyTrackingInspection tool = registerInspection(new MyTrackingInspection());

    configureByText(JavaFileType.INSTANCE, "class X { void f() { <caret> } }");
    waitForDaemon();
    tool.visited.clear();

    getPsiManager().dropPsiCaches();

    waitForDaemon();
    assertNotEmpty(tool.visited);
  }

  public void testOverriddenMethodMarkers() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    highlightErrors();

    Document document = getEditor().getDocument();
    List<LineMarkerInfo> markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertEquals(3, markers.size());

    type("//xxxx");

    highlightErrors();
    markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertEquals(3, markers.size());
  }


  public void testOverriddenMethodMarkersDoNotClearedByChangingWhitespaceNearby() throws Exception {
    configureByFile(BASE_PATH + "OverriddenMethodMarkers.java");
    highlightErrors();

    Document document = getEditor().getDocument();
    List<LineMarkerInfo> markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertEquals(markers.toString(), 3, markers.size());

    PsiElement element = ((PsiJavaFile)myFile).getClasses()[0].findMethodsByName("f", false)[0].getReturnTypeElement().getNextSibling();
    assertEquals("   ", element.getText());
    getEditor().getCaretModel().moveToOffset(element.getTextOffset() + 1);
    type(" ");

    highlightErrors();
    markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertEquals(markers.toString(), 3, markers.size());
  }


  public void testChangeXmlIncludeLeadsToRehighlight() {
    LanguageFilter[] extensions = ((CompositeLanguage)StdLanguages.XML).getLanguageExtensions();
    for (LanguageFilter extension : extensions) {
      ((CompositeLanguage)StdLanguages.XML).unregisterLanguageExtension(extension);
    }

    final String location = getTestName(false) + ".xsd";
    final String url = "http://myschema/";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());

    configureByFiles(null, BASE_PATH + getTestName(false) + ".xml", BASE_PATH + getTestName(false) + ".xsd");

    Collection<HighlightInfo> errors = highlightErrors();
    assertEquals(0, errors.size());

    Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    Editor schemaEditor = null;
    for (Editor editor : allEditors) {
      Document document = editor.getDocument();
      PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
      if (file == null) continue;
      if (location.equals(file.getName())) {
        schemaEditor = editor;
        break;
      }
    }
    delete(ObjectUtils.assertNotNull(schemaEditor));

    errors = highlightErrors();
    assertFalse(errors.isEmpty());

    for (LanguageFilter extension : extensions) {
      ((CompositeLanguage)StdLanguages.XML).registerLanguageExtension(extension);
    }
  }


  public void testRehighlightInnerBlockAfterInline() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    Collection<HighlightInfo> errors = highlightErrors();
    HighlightInfo error = assertOneElement(errors);
    assertEquals("Variable 'e' is already defined in the scope", error.getDescription());
    PsiElement element = getFile().findElementAt(getEditor().getCaretModel().getOffset()).getParent();

    DataContext dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT.getName(), element, ((EditorEx)getEditor()).getDataContext());
    new InlineRefactoringActionHandler().invoke(getProject(), getEditor(), getFile(), dataContext);

    Collection<HighlightInfo> afterTyping = highlightErrors();
    assertEmpty(afterTyping);
  }


  public void testRangeMarkersDoNotGetAddedOrRemovedWhenUserIsJustTypingInsideHighlightedRegionAndEspeciallyInsideInjectedFragmentsWhichAreColoredGreenAndUsersComplainEndlesslyThatEditorFlickersThere() {
    configureByText(JavaFileType.INSTANCE, "class S { int f() {\n" +
                                           "    return <caret>hashCode();\n" +
                                           "}}");

    Collection<HighlightInfo> infos = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertEquals(3, infos.size());

    final int[] count = {0};
    MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(getFile()), getProject(), true);
    modelEx.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener.Adapter() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        count[0]++;
      }

      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        count[0]++;
      }
    });

    type(' ');
    highlightErrors();

    assertEquals(0, count[0]);
  }

  public void testLineMarkersReuse() throws Throwable {
    configureByFile(BASE_PATH + "LineMarkerChange.java");

    List<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);

    List<LineMarkerInfo> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
    assertSize(5, lineMarkers);

    type('X');

    final Collection<String> changed = new ArrayList<>();
    MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(getFile()), getProject(), true);
    modelEx.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        changed(highlighter, ExceptionUtil.getThrowableText(new Throwable("after added")));
      }

      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        changed(highlighter, ExceptionUtil.getThrowableText(new Throwable("before removed")));
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleChanged) {
        changed(highlighter, ExceptionUtil.getThrowableText(new Throwable("changed")));
      }

      private void changed(@NotNull RangeHighlighterEx highlighter, String reason) {
        if (highlighter.getTargetArea() != HighlighterTargetArea.LINES_IN_RANGE) return; // not line marker
        List<LineMarkerInfo> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
        if (ContainerUtil.find(lineMarkers, lm -> lm.highlighter == highlighter) == null) return; // not line marker

        changed.add(highlighter+": \n"+reason);
      }
    });

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    List<HighlightInfo> infosAfter = CodeInsightTestFixtureImpl.instantiateAndRun(myFile, myEditor, new int[]{/*Pass.UPDATE_ALL, Pass.LOCAL_INSPECTIONS*/}, false);
    assertNotEmpty(filter(infosAfter, HighlightSeverity.ERROR));

    assertEmpty(changed);
    List<LineMarkerInfo> lineMarkersAfter = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
    assertEquals(lineMarkersAfter.size(), lineMarkers.size());
  }

  public void testLineMarkersDoNotBlinkOnBackSpaceRightBeforeMethodIdentifier() {
    configureByText(JavaFileType.INSTANCE, "package x; \n" +
                                           "class  <caret>ToRun{\n" +
                                           "  public static void main(String[] args) {\n"+
                                           "  }\n"+
                                           "}");

    List<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);

    List<LineMarkerInfo> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
    assertSize(2, lineMarkers);

    backspace();

    final Collection<String> changed = new ArrayList<>();
    MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(getFile()), getProject(), true);
    modelEx.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        changed(highlighter, ExceptionUtil.getThrowableText(new Throwable("after added")));
      }

      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        changed(highlighter, ExceptionUtil.getThrowableText(new Throwable("before removed")));
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleChanged) {
        changed(highlighter, ExceptionUtil.getThrowableText(new Throwable("changed")));
      }

      private void changed(@NotNull RangeHighlighterEx highlighter, String reason) {
        if (highlighter.getTargetArea() != HighlighterTargetArea.LINES_IN_RANGE) return; // not line marker
        List<LineMarkerInfo> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
        if (ContainerUtil.find(lineMarkers, lm -> lm.highlighter == highlighter) == null) return; // not line marker

        changed.add(highlighter+": \n"+reason);
      }
    });

    assertEmpty(highlightErrors());

    assertSize(2, DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject()));

    assertEmpty(changed);
  }

  public void testTypeParametersMustNotBlinkWhenTypingInsideClass() {
    configureByText(JavaFileType.INSTANCE, "package x; \n" +
                                           "abstract class ToRun<TTTTTTTTTTTTTTT> implements Comparable<TTTTTTTTTTTTTTT> {\n" +
                                           "  private ToRun<TTTTTTTTTTTTTTT> delegate;\n"+
                                           "  <caret>\n"+
                                           "  \n"+
                                           "}");

    List<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);

    MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(getFile()), getProject(), true);
    modelEx.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener.Adapter() {
      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        if (TextRange.create(highlighter).substring(highlighter.getDocument().getText()).equals("TTTTTTTTTTTTTTT")) {
          throw new RuntimeException("Must not remove type parameter highlighter");
        }
      }
    });

    assertEmpty(highlightErrors());

    type("//xxx");
    assertEmpty(highlightErrors());
    backspace();
    assertEmpty(highlightErrors());
    backspace();
    assertEmpty(highlightErrors());
    backspace();
    assertEmpty(highlightErrors());
    backspace();
    backspace();
    assertEmpty(highlightErrors());
  }

  public void testLocallyUsedFieldHighlighting() {
    configureByText(JavaFileType.INSTANCE, "class A {\n" +
                                           "    String cons;\n" +
                                           "    void foo() {\n" +
                                           "        String local = null;\n" +
                                           "        <selection>cons</selection>.substring(1);" +
                                           "    }\n" +
                                           "    public static void main(String[] args) {\n" +
                                           "        new A().foo();\n" +
                                           "    }" +
                                           "}");
    enableInspectionTool(new UnusedDeclarationInspection(true));

    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertSize(1, infos);
    assertEquals("Variable 'local' is never used", infos.get(0).getDescription());

    type("local");

    infos = doHighlighting(HighlightSeverity.WARNING);
    assertSize(1, infos);
    assertEquals("Field 'cons' is never used", infos.get(0).getDescription());
  }

  public void testOverrideMethodsHighlightingPersistWhenTypeInsideMethodBody() {
    configureByText(JavaFileType.INSTANCE, "package x; \n" +
                                           "class ClassA {\n" +
                                           "    static <T> void sayHello(Class<? extends T> msg) {}\n" +
                                           "}\n" +

                                           "class ClassB extends ClassA {\n" +
                                           "    static <T extends String> void sayHello(Class<? extends T> msg) {<caret>\n" +
                                           "    }\n" +
                                           "}\n");

    assertSize(1, highlightErrors());
    type("//my comment inside method body, so class modifier won't be visited");
    assertSize(1, highlightErrors());
  }

  public void testLineMarkersClearWhenTypingAtTheEndOfPsiComment() {
    configureByText(JavaFileType.INSTANCE, "class S {\n//ddd<caret>\n}");
    StringBuffer log = new StringBuffer();
    final LineMarkerProvider provider = new LineMarkerProvider() {
      @Nullable
      @Override
      public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
        String msg = "provider.getLineMarkerInfo(" + element + ") called\n";
        LineMarkerInfo<PsiComment> info = null;
        if (element instanceof PsiComment) {
          info = new LineMarkerInfo<>((PsiComment)element, element.getTextRange(), null, Pass.LINE_MARKERS, null, null, GutterIconRenderer.Alignment.LEFT);
          msg += " provider info: "+info + "\n";
        }
        log.append(msg);
        return info;
      }

      @Override
      public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {

      }
    };
    LineMarkerProviders.INSTANCE.addExplicitExtension(JavaLanguage.INSTANCE, provider);
    Disposer.register(getTestRootDisposable(), () -> LineMarkerProviders.INSTANCE.removeExplicitExtension(JavaLanguage.INSTANCE, provider));
    myDaemonCodeAnalyzer.restart();
    try {
      TextRange range = ObjectUtils.assertNotNull(FileStatusMap.getDirtyTextRange(getEditor(), Pass.UPDATE_ALL));
      log.append("FileStatusMap.getDirtyTextRange: " + range+"\n");
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(getFile(), range.getStartOffset(), range.getEndOffset());
      log.append("CollectHighlightsUtil.getElementsInRange" + range + ": " + elements.size() +" elements : "+ elements+"\n");
      List<HighlightInfo> infos = doHighlighting();
      log.append(" File text: '" + getFile().getText() + "'\n");
      log.append("infos: " + infos + "\n");
      assertEmpty(filter(infos,HighlightSeverity.ERROR));

      List<LineMarkerInfo> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
      assertOneElement(lineMarkers);

      type(' ');
      infos = doHighlighting();
      log.append("File text: '" + getFile().getText() + "'\n");
      log.append("infos: " + infos + "\n");
      assertEmpty(filter(infos,HighlightSeverity.ERROR));

      lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
      assertOneElement(lineMarkers);

      backspace();
      infos = doHighlighting();
      log.append("File text: '" + getFile().getText() + "'\n");
      log.append("infos: " + infos + "\n");
      assertEmpty(filter(infos,HighlightSeverity.ERROR));

      lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
      assertOneElement(lineMarkers);
    }
    catch (AssertionError e) {
      System.err.println("Log:\n"+log+"\n---");
      throw e;
    }
  }

  public void testWhenTypingOverWrongReferenceItsColorChangesToBlackAndOnlyAfterHighlightingFinishedItReturnsToRed() {
    configureByText(StdFileTypes.JAVA, "class S {  int f() {\n" +
                                       "    return asfsdfsdfsd<caret>;\n" +
                                       "}}");

    Collection<HighlightInfo> errors = highlightErrors();
    assertOneElement(errors);
    assertSame(HighlightInfoType.WRONG_REF, errors.iterator().next().type);

    Document document = getDocument(getFile());

    type("xxx");

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightInfoType.SYMBOL_TYPE_SEVERITY, getProject());
    for (HighlightInfo info : infos) {
      assertNotSame(HighlightInfoType.WRONG_REF, info.type);
    }

    errors = highlightErrors();
    assertOneElement(errors);
    assertSame(HighlightInfoType.WRONG_REF, errors.iterator().next().type);
  }


  public void testQuickFixRemainsAvailableAfterAnotherFixHasBeenAppliedInTheSameCodeBlockBefore() throws Exception {
    configureByFile(BASE_PATH + "QuickFixes.java");

    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean old = settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST;
    settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = true;

    try {
      Collection<HighlightInfo> errors = highlightErrors();
      assertEquals(3, errors.size());
      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());

      List<IntentionAction> fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
      IntentionAction fix = assertContainsOneOf(fixes, "Delete catch for 'java.io.IOException'");

      final IntentionAction finalFix = fix;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> finalFix.invoke(getProject(), getEditor(), getFile()));

      errors = highlightErrors();
      assertEquals(2, errors.size());

      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());
      fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
      fix = assertContainsOneOf(fixes, "Delete catch for 'java.io.IOException'");

      final IntentionAction finalFix1 = fix;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> finalFix1.invoke(getProject(), getEditor(), getFile()));

      errors = highlightErrors();
      assertOneElement(errors);

      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());
      fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
      fix = assertContainsOneOf(fixes, "Delete catch for 'java.io.IOException'");

      final IntentionAction finalFix2 = fix;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> finalFix2.invoke(getProject(), getEditor(), getFile()));

      errors = highlightErrors();
      assertEmpty(errors);
    }
    finally {
      settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = old;
    }
  }

  private static IntentionAction assertContainsOneOf(@NotNull Collection<IntentionAction> collection, @NotNull String text) {
    IntentionAction result = null;
    for (IntentionAction action : collection) {
      if (text.equals(action.getText())) {
        if (result != null) {
          fail("multiple " + " objects present in collection " + collection);
        }
        else {
          result = action;
        }
      }
    }
    assertNotNull(" object not found in collection " + collection, result);
    return result;
  }


  public void testRangeHighlightersDoNotGetStuckForever() {
    configureByText(StdFileTypes.JAVA, "class S { void ffffff() {fff<caret>fff();}}");

    List<HighlightInfo> infos = highlightErrors();
    assertEmpty(infos);
    MarkupModel markup = DocumentMarkupModel.forDocument(myEditor.getDocument(), getProject(), true);
    final TextRange[] highlightersBefore = getHighlightersTextRange(markup);

    type("%%%%");
    highlightErrors();
    backspace();
    backspace();
    backspace();
    backspace();
    infos = highlightErrors();
    assertEmpty(infos);

    final TextRange[] highlightersAfter = getHighlightersTextRange(markup);

    assertEquals(highlightersBefore.length, highlightersAfter.length);
    for (int i = 0; i < highlightersBefore.length; i++) {
      TextRange before = highlightersBefore[i];
      TextRange after = highlightersAfter[i];
      assertEquals(before.getStartOffset(), after.getStartOffset());
      assertEquals(before.getEndOffset(), after.getEndOffset());
    }
  }

  @NotNull
  private static TextRange[] getHighlightersTextRange(@NotNull MarkupModel markup) {
    final RangeHighlighter[] highlighters = markup.getAllHighlighters();

    final TextRange[] result = new TextRange[highlighters.length];
    for (int i = 0; i < highlighters.length; i++) {
      result[i] = ProperTextRange.create(highlighters[i]);
    }
    return orderByHashCode(result); // markup.getAllHighlighters returns unordered array
  }

  @NotNull
  private static <T extends Segment> T[] orderByHashCode(@NotNull T[] highlighters) {
    Arrays.sort(highlighters, (o1, o2) -> o2.hashCode() - o1.hashCode());
    return highlighters;
  }

  public void testFileStatusMapDirtyCachingWorks() {
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(false); // to prevent auto-start highlighting
    UIUtil.dispatchAllInvocationEvents();
    configureByText(StdFileTypes.JAVA, "class <caret>S { int ffffff =  0;}");
    UIUtil.dispatchAllInvocationEvents();

    final int[] creation = {0};
    class Fac extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
      private Fac(Project project) {
        super(project);
      }

      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
        TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
        if (textRange == null) return null;
        return new MyPass(myProject);
      }

      class MyPass extends TextEditorHighlightingPass {
        private MyPass(final Project project) {
          super(project, getEditor().getDocument(), false);
          creation[0]++;
        }

        @Override
        public void doCollectInformation(@NotNull ProgressIndicator progress) {
        }

        @Override
        public void doApplyInformationToEditor() {
        }
      }
    }
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(getProject());
    registrar.registerTextEditorHighlightingPass(new Fac(getProject()), null, null, false, -1);
    highlightErrors();
    assertEquals(1, creation[0]);

    //cached
    highlightErrors();
    assertEquals(1, creation[0]);
    highlightErrors();
    assertEquals(1, creation[0]);

    type(' ');
    highlightErrors();
    assertEquals(2, creation[0]);
    highlightErrors();
    assertEquals(2, creation[0]);
    highlightErrors();
    assertEquals(2, creation[0]);
  }


  public void testDefensivelyDirtyFlagDoesNotClearPrematurely() {
    class Fac extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
      private Fac(Project project) {
        super(project);
      }

      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
        return null;
      }
    }
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(getProject());
    registrar.registerTextEditorHighlightingPass(new Fac(getProject()), null, null, false, -1);

    configureByText(StdFileTypes.JAVA, "@Deprecated<caret> class S { } ");

    List<HighlightInfo> infos = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertEquals(2, infos.size());

    assertEquals("@Deprecated", infos.get(0).getText());
    assertEquals("S", infos.get(1).getText());

    backspace();
    type('d');

    List<HighlightInfo> after = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);

    assertEquals("@Deprecated", after.get(0).getText());
    assertEquals("S", after.get(1).getText());

    backspace();
    type('d');

    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getTextLength());
    type(" ");

    after = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertEquals(2, after.size());

    assertEquals("@Deprecated", after.get(0).getText());
    assertEquals("S", after.get(1).getText());
  }


  public void testModificationInsideCodeblockDoesnotAffectErrorMarkersOutside() throws Exception {
    configureByFile(BASE_PATH + "ErrorMark.java");
    List<HighlightInfo> errs = highlightErrors();
    assertEquals(1, errs.size());
    assertEquals("'}' expected", errs.get(0).getDescription());

    type("//comment");
    errs = highlightErrors();
    assertEquals(1, errs.size());
    assertEquals("'}' expected", errs.get(0).getDescription());
  }

  public void testErrorMarkerAtTheEndOfTheFile() {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      try {
        configureByFile(BASE_PATH + "ErrorMarkAtEnd.java");
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }, "cc", this);
    List<HighlightInfo> errs = highlightErrors();
    assertEmpty(errs);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      Document document = getEditor().getDocument();
      int offset = getEditor().getCaretModel().getOffset();
      while (offset < document.getTextLength()) {
        int i = StringUtil.indexOf(document.getText(), '}', offset, document.getTextLength());
        if (i == -1) break;
        getEditor().getCaretModel().moveToOffset(i);
        delete(getEditor());
      }
    }, "my", this);

    errs = highlightErrors();
    assertEquals(2, errs.size());
    assertEquals("'}' expected", errs.get(0).getDescription());

    undo();
    errs = highlightErrors();
    assertEmpty(errs);
  }


  // disabled for now
  public void _testSOEInEndlessAppendChainPerformance() {
    StringBuilder text = new StringBuilder("class S { String ffffff =  new StringBuilder()\n");
    for (int i=0; i<2000; i++) {
      text.append(".append(").append(i).append(")\n");
    }
    text.append(".toString();<caret>}");
    configureByText(StdFileTypes.JAVA, text.toString());

    PlatformTestUtil.startPerformanceTest("tree visitors", 30000, () -> {
      List<HighlightInfo> infos = highlightErrors();
      assertEmpty(infos);
      type("kjhgas");
      List<HighlightInfo> errors = highlightErrors();
      assertFalse(errors.isEmpty());
      backspace();
      backspace();
      backspace();
      backspace();
      backspace();
      backspace();
      infos = highlightErrors();
      assertEmpty(infos);
    }).useLegacyScaling().assertTiming();
  }


  public void testBulbAppearsAfterType() {
    String text = "class S { ArrayList<caret>XXX x;}";
    configureByText(StdFileTypes.JAVA, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    final Set<LightweightHint> shown = ContainerUtil.newIdentityTroveSet();
    getProject().getMessageBus().connect().subscribe(EditorHintListener.TOPIC, (project, hint, flags) -> {
      shown.add(hint);
      hint.addHintListener(event -> shown.remove(hint));
    });

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    highlightErrors();

    IntentionHintComponent hintComponent = codeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));

    type("x");
    highlightErrors();
    hintComponent = codeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));
  }

  @Override
  protected void configureByExistingFile(@NotNull VirtualFile virtualFile) {
    super.configureByExistingFile(virtualFile);
    EditorTracker editorTracker = getProject().getComponent(EditorTracker.class);
    editorTracker.setActiveEditors(Collections.singletonList(getEditor()));
  }

  @Override
  protected VirtualFile configureByFiles(@Nullable File rawProjectRoot, @NotNull VirtualFile... vFiles) throws IOException {
    VirtualFile file = super.configureByFiles(rawProjectRoot, vFiles);
    EditorTracker editorTracker = getProject().getComponent(EditorTracker.class);
    editorTracker.setActiveEditors(Collections.singletonList(getEditor()));
    return file;
  }

  public void testDaemonIgnoresFrameDeactivation() {
    // return default value to avoid unnecessary save
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    String text = "class S { ArrayList<caret>XXX x;}";
    configureByText(StdFileTypes.JAVA, text);
    highlightErrors();

    GeneralSettings settings = GeneralSettings.getInstance();
    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    boolean frameSave = settings.isSaveOnFrameDeactivation();
    settings.setSaveOnFrameDeactivation(true);
    UtilKt.runInAllowSaveMode(() -> {
      try {
        StoreUtil.saveDocumentsAndProjectsAndApp(false);

        checkDaemonReaction(false, () -> StoreUtil.saveDocumentsAndProjectsAndApp(false));
      }
      finally {
        settings.setSaveOnFrameDeactivation(frameSave);
      }
      return Unit.INSTANCE;
    });
  }

  public void testApplyLocalQuickFix() {
    configureByText(StdFileTypes.JAVA, "class X { static int sss; public int f() { return this.<caret>sss; }}");

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    List<HighlightInfo> warns = doHighlighting(HighlightSeverity.WARNING);
    assertOneElement(warns);
    List<HighlightInfo.IntentionActionDescriptor> actions = ShowIntentionsPass.getAvailableFixes(getEditor(), getFile(), -1);
    final HighlightInfo.IntentionActionDescriptor descriptor = assertOneElement(actions);
    CodeInsightTestFixtureImpl.invokeIntention(descriptor.getAction(), getFile(), getEditor(), "");

    highlightErrors();
    assertEmpty(ShowIntentionsPass.getAvailableFixes(getEditor(), getFile(), -1));
  }


  public void testApplyErrorInTheMiddle() {
    String text = "class <caret>X { ";
    for (int i = 0; i < 100; i++) {
      text += "\n    {\n" +
              "//    String x = \"<zzzzzzzzzz/>\";\n" +
              "    }";
    }
    text += "\n}";
    configureByText(StdFileTypes.JAVA, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    List<HighlightInfo> warns = highlightErrors();
    assertEmpty(warns);

    type("//");
    List<HighlightInfo> errors = highlightErrors();
    assertEquals(2, errors.size());

    backspace();
    backspace();

    errors = highlightErrors();
    assertEmpty(errors);
  }


  public void testErrorInTheEndOutsideVisibleArea() {
    String text = "<xml> \n" + StringUtil.repeatSymbol('\n', 1000) + "</xml>\nxxxxx<caret>";
    configureByText(StdFileTypes.XML, text);

    ProperTextRange visibleRange = makeEditorWindowVisible(new Point(0, 1000), myEditor);
    assertTrue(visibleRange.getStartOffset() > 0);

    List<HighlightInfo> warns = highlightErrors();
    HighlightInfo info = assertOneElement(warns);
    assertEquals("Top level element is not completed", info.getDescription());

    type("xxx");
    List<HighlightInfo> errors = highlightErrors();
    info = assertOneElement(errors);
    assertEquals("Top level element is not completed", info.getDescription());
  }

  public static ProperTextRange makeEditorWindowVisible(Point viewPosition, Editor editor) {
    ((EditorImpl)editor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ((EditorImpl)editor).getScrollPane().getViewport().setViewPosition(viewPosition);
    ((EditorImpl)editor).getScrollPane().getViewport().setExtentSize(new Dimension(100, ((EditorImpl)editor).getPreferredHeight() -
                                                                                        viewPosition.y));
    return VisibleHighlightingPassFactory.calculateVisibleRange(editor);
  }


  public void testEnterInCodeBlock() {
    String text = "class LQF {\n" +
                  "    int wwwwwwwwwwww;\n" +
                  "    public void main() {<caret>\n" +
                  "        wwwwwwwwwwww = 1;\n" +
                  "    }\n" +
                  "}";
    configureByText(StdFileTypes.JAVA, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);

    List<HighlightInfo> infos = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertEquals(4, infos.size());

    type('\n');
    infos = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertEquals(4, infos.size());

    deleteLine();

    infos = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertEquals(4, infos.size());
  }


  public void testTypingNearEmptyErrorElement() {
    String text = "class LQF {\n" +
                  "    public void main() {\n" +
                  "        int wwwwwwwwwwww = 1<caret>\n" +
                  "    }\n" +
                  "}";
    configureByText(StdFileTypes.JAVA, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);

    List<HighlightInfo> infos = highlightErrors();
    assertEquals(1, infos.size());

    type(';');
    infos = highlightErrors();
    assertEmpty(infos);
  }


  public void testLIPGetAllParentsAfterCodeBlockModification() {
    @Language("JAVA")
    String text = "class LQF {\n" +
                  "    int f;\n" +
                  "    public void me() {\n" +
                  "        //<caret>\n" +
                  "    }\n" +
                  "}";
    configureByText(StdFileTypes.JAVA, text);

    final List<PsiElement> visitedElements = Collections.synchronizedList(new ArrayList<>());
    class MyVisitor extends PsiElementVisitor {
      @Override
      public void visitElement(PsiElement element) {
        visitedElements.add(element);
      }
    }
    final LocalInspectionTool tool = new LocalInspectionTool() {
      @Nls
      @NotNull
      @Override
      public String getGroupDisplayName() {
        return "fegna";
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
        return new MyVisitor();
      }
    };
    disposeOnTearDown(() -> disableInspectionTool(tool.getShortName()));
    enableInspectionTool(tool);

    List<HighlightInfo> infos = highlightErrors();
    assertEmpty(infos);

    List<PsiElement> allPsi = CollectHighlightsUtil.getElementsInRange(myFile, 0, myFile.getTextLength());
    assertEquals(new HashSet<>(allPsi), new HashSet<>(visitedElements));

    // inside code block modification
    visitedElements.clear();
    backspace();
    backspace();

    infos = highlightErrors();
    assertEmpty(infos);

    PsiMethod method = ((PsiJavaFile)myFile).getClasses()[0].getMethods()[0];
    List<PsiElement> methodAndParents =
      CollectHighlightsUtil.getElementsInRange(myFile, method.getTextRange().getStartOffset(), method.getTextRange().getEndOffset(), true);
    assertEquals(new HashSet<>(methodAndParents), new HashSet<>(visitedElements));
  }


  public void testCancelsItSelfOnTypingInAlienProject() throws Throwable {
    String body = StringUtil.repeat("\"String field = null;\"\n", 1000);
    configureByText(StdFileTypes.JAVA, "class X{ void f() {" + body + "<caret>\n} }");

    File temp = createTempDirectory();
    final Project alienProject = createProject(temp + "/alien.ipr", DebugUtil.currentStackTrace());
    boolean succ2 = ProjectManagerEx.getInstanceEx().openProject(alienProject);
    assertTrue(succ2);
    DaemonProgressIndicator.setDebug(true);

    try {
      Module alienModule = doCreateRealModuleIn("x", alienProject, getModuleType());
      final VirtualFile alienRoot = PsiTestUtil.createTestProjectStructure(alienProject, alienModule, myFilesToDelete);
      OpenFileDescriptor alienDescriptor = new WriteAction<OpenFileDescriptor>() {
        @Override
        protected void run(@NotNull Result<OpenFileDescriptor> result) throws Throwable {
          VirtualFile alienFile = alienRoot.createChildData(this, "X.java");
          setFileText(alienFile, "class Alien { }");
          OpenFileDescriptor alienDescriptor = new OpenFileDescriptor(alienProject, alienFile);
          result.setResult(alienDescriptor);
        }
      }.execute().throwException().getResultObject();

      FileEditorManager fe = FileEditorManager.getInstance(alienProject);
      final Editor alienEditor = ObjectUtils.assertNotNull(fe.openTextEditor(alienDescriptor, false));
      ((EditorImpl)alienEditor).setCaretActive();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      PsiDocumentManager.getInstance(alienProject).commitAllDocuments();

      // start daemon in main project. should check for its cancel when typing in alien
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
      DaemonCodeAnalyzerImpl di = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
      final boolean[] checked = {false};
      Runnable callbackWhileWaiting = () -> {
        if (checked[0]) return;
        checked[0] = true;
        typeInAlienEditor(alienEditor, 'x');
      };
      di.runPasses(getFile(), getEditor().getDocument(), Collections.singletonList(textEditor), ArrayUtil.EMPTY_INT_ARRAY, true, callbackWhileWaiting);
    }
    catch (ProcessCanceledException ignored) {
      //DaemonProgressIndicator.setDebug(true);
      //System.out.println("indicator = " + indicator[0]);
      return;
    }
    finally {
      ProjectManagerEx.getInstanceEx().closeAndDispose(alienProject);
    }
    fail("must throw PCE");
  }

  public void testPasteInAnonymousCodeBlock() {
    configureByText(StdFileTypes.JAVA, "class X{ void f() {" +
                                       "     int x=0;\n" +
                                       "    Runnable r = new Runnable() { public void run() {\n" +
                                       " <caret>\n" +
                                       "    }};\n" +
                                       "    <selection>int y = x;</selection>\n " +
                                       "\n} }");
    assertEmpty(highlightErrors());
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_EDITOR_COPY);
    assertEquals("int y = x;", getEditor().getSelectionModel().getSelectedText());
    getEditor().getSelectionModel().removeSelection();
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_EDITOR_PASTE);
    List<HighlightInfo> errors = highlightErrors();
    assertEquals(1, errors.size());
  }

  public void testReactivityPerformance() throws Throwable {
    @NonNls String filePath = "/psi/resolve/Thinlet.java";
    configureByFile(filePath);
    type(' ');
    CompletionContributor.forLanguage(getFile().getLanguage());
    highlightErrors();

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    int N = Math.max(5, Timings.adjustAccordingToMySpeed(80, false));
    LOG.debug("N = " + N);
    final long[] interruptTimes = new long[N];
    for (int i = 0; i < N; i++) {
      codeAnalyzer.restart();
      final int finalI = i;
      final long start = System.currentTimeMillis();
      final AtomicLong typingStart = new AtomicLong();
      final AtomicReference<RuntimeException> exception = new AtomicReference<>();
      Thread watcher = new Thread("reactivity watcher") {
        @Override
        public void run() {
          while (true) {
            final long start1 = typingStart.get();
            if (start1 == -1) break;
            if (start1 == 0) {
              try {
                Thread.sleep(5);
              }
              catch (InterruptedException e1) {
                throw new RuntimeException(e1);
              }
              continue;
            }
            long elapsed = System.currentTimeMillis() - start1;
            if (elapsed > 500) {
              // too long, see WTF
              String message = "Too long interrupt: " + elapsed +
                               "; Progress: " + codeAnalyzer.getUpdateProgress() +
                               "\n----------------------------";
              dumpThreadsToConsole();
              exception.set(new RuntimeException(message));
              throw exception.get();
            }
          }
        }
      };
      try {
        PsiFile file = getFile();
        Editor editor = getEditor();
        Project project = file.getProject();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        watcher.start();
        Runnable interrupt = () -> {
          long now = System.currentTimeMillis();
          if (now - start < 100) {
            // wait to engage all highlighting threads
            return;
          }
          typingStart.set(System.currentTimeMillis());
          type(' ');
          long end = System.currentTimeMillis();
          long interruptTime = end - now;
          interruptTimes[finalI] = interruptTime;
          assertTrue(codeAnalyzer.getUpdateProgress().isCanceled());
          System.out.println(interruptTime);
          throw new ProcessCanceledException();
        };
        long hiStart = System.currentTimeMillis();
        codeAnalyzer
          .runPasses(file, editor.getDocument(), Collections.singletonList(textEditor), ArrayUtil.EMPTY_INT_ARRAY, false, interrupt);
        long hiEnd = System.currentTimeMillis();
        DaemonProgressIndicator progress = codeAnalyzer.getUpdateProgress();
        String message = "Should have been interrupted: " + progress + "; Elapsed: " + (hiEnd - hiStart) + "ms";
        dumpThreadsToConsole();
        throw new RuntimeException(message);
      }
      catch (ProcessCanceledException ignored) {
      }
      finally {
        typingStart.set(-1); // cancel watcher
        watcher.join();
        if (exception.get() != null) {
          throw exception.get();
        }
      }
    }

    long ave = ArrayUtil.averageAmongMedians(interruptTimes, 3);
    System.out.println("Average among the N/3 median times: " + ave + "ms");
    assertTrue(ave < 300);
  }

  private static void dumpThreadsToConsole() {
    System.err.println("----all threads---");
    for (Thread thread : Thread.getAllStackTraces().keySet()) {

      boolean canceled = CoreProgressManager.isCanceledThread(thread);
      if (canceled) {
        System.err.println("Thread " + thread + " indicator is canceled");
      }
    }
    PerformanceWatcher.dumpThreadsToConsole("");
    System.err.println("----///////---");
  }

  public void testTypingLatencyPerformance() throws Throwable {
    @NonNls String filePath = "/psi/resolve/ThinletBig.java";

    configureByFile(filePath);

    type(' ');
    CompletionContributor.forLanguage(getFile().getLanguage());
    long s = System.currentTimeMillis();
    highlightErrors();
    long e = System.currentTimeMillis();
    //System.out.println("Hi elapsed: "+(e-s));

    //List<String> dumps = new ArrayList<>();

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    int N = Math.max(5, Timings.adjustAccordingToMySpeed(80, false));
    LOG.debug("N = " + N);
    final long[] interruptTimes = new long[N];
    for (int i = 0; i < N; i++) {
      codeAnalyzer.restart();
      final int finalI = i;
      final long start = System.currentTimeMillis();
      Runnable interrupt = () -> {
        long now = System.currentTimeMillis();
        if (now - start < 100) {
          // wait to engage all highlighting threads
          return;
        }
        // uncomment to debug what's causing pauses
        /*
        AtomicBoolean finished = new AtomicBoolean();
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
          if (!finished.get()) {
            dumps.add(ThreadDumper.dumpThreadsToString());
          }
        }, 10, TimeUnit.MILLISECONDS);
        */
        type(' ');
        long end = System.currentTimeMillis();
        //finished.set(true);
        long interruptTime = end - now;
        interruptTimes[finalI] = interruptTime;
        assertTrue(codeAnalyzer.getUpdateProgress().isCanceled());
        throw new ProcessCanceledException();
      };
      try {
        PsiFile file = getFile();
        Editor editor = getEditor();
        Project project = file.getProject();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        codeAnalyzer
          .runPasses(file, editor.getDocument(), Collections.singletonList(textEditor), ArrayUtil.EMPTY_INT_ARRAY, false, interrupt);

        throw new RuntimeException("should have been interrupted");
      }
      catch (ProcessCanceledException ignored) {
      }
      backspace();
      //highlightErrors();
    }

    System.out.println("Interrupt times: " + Arrays.toString(interruptTimes));

    /*
    for (String dump : dumps) {
      System.out.println("\n\n-----------------------------\n\n" + dump);
    }
    */

    long mean = ArrayUtil.averageAmongMedians(interruptTimes, 3);
    long avg = Arrays.stream(interruptTimes).sum() / interruptTimes.length;
    long max = Arrays.stream(interruptTimes).max().getAsLong();
    long min = Arrays.stream(interruptTimes).min().getAsLong();
    System.out.println("Average among the N/3 median times: " + mean + "ms; max: "+max+"; min:"+min+"; avg: "+avg);
    assertTrue(String.valueOf(mean), mean < 10);
  }

  private static void startCPUProfiling() {
    try {
      Class<?> aClass = Class.forName("com.intellij.util.ProfilingUtil");
      Method method = ObjectUtils.assertNotNull(ReflectionUtil.getDeclaredMethod(aClass, "startCPUProfiling"));
      method.invoke(null);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  private static void stopCPUProfiling() {
    try {
      Class<?> aClass = Class.forName("com.intellij.util.ProfilingUtil");
      Method method = ObjectUtils.assertNotNull(ReflectionUtil.getDeclaredMethod(aClass, "stopCPUProfiling"));
      method.invoke(null);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String captureCPUSnapshot() {
    try {
      Class<?> aClass = Class.forName("com.intellij.util.ProfilingUtil");
      Method method = ObjectUtils.assertNotNull(ReflectionUtil.getDeclaredMethod(aClass, "captureCPUSnapshot"));
      return (String)method.invoke(null);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void testPostHighlightingPassRunsOnEveryPsiModification() throws Exception {
    PsiFile x = createFile("X.java", "public class X { public static void ffffffffffffff(){} }");
    PsiFile use = createFile("Use.java", "public class Use { { <caret>X.ffffffffffffff(); } }");
    configureByExistingFile(use.getVirtualFile());

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    HighlightDisplayKey myDeadCodeKey = HighlightDisplayKey.find(UnusedDeclarationInspectionBase.SHORT_NAME);
    if (myDeadCodeKey == null) {
      myDeadCodeKey = HighlightDisplayKey.register(UnusedDeclarationInspectionBase.SHORT_NAME, UnusedDeclarationInspectionBase.DISPLAY_NAME);
    }
    UnusedDeclarationInspectionBase myDeadCodeInspection = new UnusedDeclarationInspectionBase(true);
    enableInspectionTool(myDeadCodeInspection);
    assert profile.isToolEnabled(myDeadCodeKey, myFile);

    Editor xEditor = createEditor(x.getVirtualFile());
    List<HighlightInfo> xInfos = filter(CodeInsightTestFixtureImpl.instantiateAndRun(x, xEditor, new int[0], false),
                                        HighlightSeverity.WARNING);
    HighlightInfo info = ContainerUtil.find(xInfos, xInfo -> xInfo.getDescription().equals("Method 'ffffffffffffff()' is never used"));
    assertNull(xInfos.toString(), info);

    Editor useEditor = myEditor;
    List<HighlightInfo> useInfos = filter(CodeInsightTestFixtureImpl.instantiateAndRun(use, useEditor, new int[0], false), HighlightSeverity.ERROR);
    assertEmpty(useInfos);

    type('/');
    type('/');

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    xInfos = filter(CodeInsightTestFixtureImpl.instantiateAndRun(x, xEditor, new int[0], false), HighlightSeverity.WARNING);
    info = ContainerUtil.find(xInfos, xInfo -> xInfo.getDescription().equals("Method 'ffffffffffffff()' is never used"));
    assertNotNull(xInfos.toString(), info);
  }


  public void testErrorDisappearsRightAfterTypingInsideVisibleAreaWhileDaemonContinuesToChugAlong() {
    String text = "class X{\nint xxx;\n{\nint i = <selection>null</selection><caret>;\n" + StringUtil.repeat("{ this.hashCode(); }\n\n\n", 10000) + "}}";
    configureByText(StdFileTypes.JAVA, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(100, 100);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ((EditorImpl)myEditor).getScrollPane().getViewport().setViewPosition(new Point(0, 0));
    ((EditorImpl)myEditor).getScrollPane().getViewport().setExtentSize(new Dimension(100, 100000));
    ProperTextRange visibleRange = VisibleHighlightingPassFactory.calculateVisibleRange(getEditor());
    assertTrue(visibleRange.getLength() > 0);
    final Document document = myEditor.getDocument();
    assertTrue(visibleRange.getLength() < document.getTextLength());

    List<HighlightInfo> err1 = highlightErrors();
    HighlightInfo info = assertOneElement(err1);
    final String errorDescription = "Incompatible types. Found: 'null', required: 'int'";
    assertEquals(errorDescription, info.getDescription());

    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myProject, false);
    final boolean[] errorRemoved = {false};

    model.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener.Adapter() {
      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        Object tt = highlighter.getErrorStripeTooltip();
        if (!(tt instanceof HighlightInfo)) return;
        String description = ((HighlightInfo)tt).getDescription();
        if (errorDescription.equals(description)) {
          errorRemoved[0] = true;

          List<TextEditorHighlightingPass> passes = myDaemonCodeAnalyzer.getPassesToShowProgressFor(document);
          GeneralHighlightingPass ghp = null;
          for (TextEditorHighlightingPass pass : passes) {
            if (pass instanceof GeneralHighlightingPass && pass.getId() == Pass.UPDATE_ALL) {
              assert ghp == null : ghp;
              ghp = (GeneralHighlightingPass)pass;
            }
          }
          assertNotNull(ghp);
          boolean finished = ghp.isFinished();
          assertFalse(finished);
        }
      }
    });
    type("1");

    List<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);
    assertTrue(errorRemoved[0]);
  }

  public void testDaemonWorksForDefaultProjectSinceItIsNeededInSettingsDialogForSomeReason() {
    assertNotNull(DaemonCodeAnalyzer.getInstance(ProjectManager.getInstance().getDefaultProject()));
  }

  public void testChangeEventsAreNotAlwaysGeneric() {
    String body = "class X {\n" +
                  "<caret>    @org.PPP\n" +
                  "    void dtoArrayDouble() {\n" +
                  "    }\n" +
                  "}";
    configureByText(JavaFileType.INSTANCE, body);
    makeEditorWindowVisible(new Point(), myEditor);

    List<HighlightInfo> errors = highlightErrors();
    assertFalse(errors.isEmpty());

    type("//");
    errors = highlightErrors();
    assertEmpty(errors);

    backspace();
    backspace();
    errors = highlightErrors();
    assertFalse(errors.isEmpty());
  }

  public void testInterruptOnTyping() throws Throwable {
    @NonNls String filePath = "/psi/resolve/Thinlet.java";
    configureByFile(filePath);
    highlightErrors();

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    codeAnalyzer.restart();
    try {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      PsiFile file = getFile();
      Editor editor = getEditor();
      Project project = file.getProject();
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
      Runnable callbackWhileWaiting = () -> type(' ');
      codeAnalyzer
        .runPasses(file, editor.getDocument(), Collections.singletonList(textEditor), ArrayUtil.EMPTY_INT_ARRAY, true, callbackWhileWaiting);
    }
    catch (ProcessCanceledException ignored) {
      return;
    }
    fail("PCE must have been thrown");
  }

  public void testAllPassesFinishAfterInterruptOnTyping_Performance() throws Throwable {
    @NonNls String filePath = "/psi/resolve/Thinlet.java";
    configureByFile(filePath);
    highlightErrors();

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    type(' ');
    for (int i=0; i<100; i++) {
      backspace();
      codeAnalyzer.restart();
      try {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();

        PsiFile file = getFile();
        Editor editor = getEditor();
        Project project = file.getProject();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        Runnable callbackWhileWaiting = () -> type(' ');
        codeAnalyzer.runPasses(file, editor.getDocument(), Collections.singletonList(textEditor), ArrayUtil.EMPTY_INT_ARRAY, true, callbackWhileWaiting);
      }
      catch (ProcessCanceledException ignored) {
        codeAnalyzer.waitForTermination();
        continue;
      }
      fail("PCE must have been thrown");
    }
  }

  public void testCodeFoldingInSplittedWindowAppliesToAllEditors() {
    final Set<Editor> applied = new THashSet<>();
    final Set<Editor> collected = new THashSet<>();
    registerFakePass(applied, collected);

    configureByText(PlainTextFileType.INSTANCE, "");
    Editor editor1 = getEditor();
    final Editor editor2 = EditorFactory.getInstance().createEditor(editor1.getDocument(),getProject());
    Disposer.register(getProject(), () -> EditorFactory.getInstance().releaseEditor(editor2));
    TextEditor textEditor1 = new PsiAwareTextEditorProvider().getTextEditor(editor1);
    TextEditor textEditor2 = new PsiAwareTextEditorProvider().getTextEditor(editor2);

    List<HighlightInfo> errors = myDaemonCodeAnalyzer.runPasses(myFile, editor1.getDocument(), Arrays.asList(textEditor1,textEditor2), new int[0], false, null);
    assertEmpty(errors);

    assertEquals(collected, ContainerUtil.newHashSet(editor1, editor2));
    assertEquals(applied, ContainerUtil.newHashSet(editor1, editor2));
  }

  private void registerFakePass(@NotNull final Set<Editor> applied, @NotNull final Set<Editor> collected) {
    class Fac extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
      private Fac(Project project) {
        super(project);
      }

      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
        return new EditorBoundHighlightingPass(editor, file, false) {
          @Override
          public void doCollectInformation(@NotNull ProgressIndicator progress) {
            collected.add(editor);
          }

          @Override
          public void doApplyInformationToEditor() {
            applied.add(editor);
          }
        };
      }
    }
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(getProject());
    registrar.registerTextEditorHighlightingPass(new Fac(getProject()), null, null, false, -1);
  }

  private volatile boolean runHeavyProcessing;
  public void testDaemonDisablesItselfDuringHeavyProcessing() {
    executeWithoutReparseDelay(() -> {
      runHeavyProcessing = false;
      try {
        final Set<Editor> applied = Collections.synchronizedSet(new THashSet<>());
        final Set<Editor> collected = Collections.synchronizedSet(new THashSet<>());
        registerFakePass(applied, collected);

        configureByText(PlainTextFileType.INSTANCE, "");
        Editor editor = getEditor();
        EditorTracker editorTracker = getProject().getComponent(EditorTracker.class);
        editorTracker.setActiveEditors(Collections.singletonList(editor));
        while (HeavyProcessLatch.INSTANCE.isRunning()) {
          UIUtil.dispatchAllInvocationEvents();
        }
        type("xxx"); // restart daemon
        assertTrue(editorTracker.getActiveEditors().contains(editor));
        assertSame(editor, FileEditorManager.getInstance(myProject).getSelectedTextEditor());


        // wait for first pass to complete
        long start = System.currentTimeMillis();
        while (myDaemonCodeAnalyzer.isRunning() || !applied.contains(editor)) {
          UIUtil.dispatchAllInvocationEvents();
          if (System.currentTimeMillis() - start > 10000) {
            fail("Too long waiting for daemon");
          }
        }

        runHeavyProcessing = true;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          AccessToken token = HeavyProcessLatch.INSTANCE.processStarted("my own heavy op");
          try {
            while (runHeavyProcessing) {
            }
          }
          finally {
            token.finish();
          }
        });
        while (!HeavyProcessLatch.INSTANCE.isRunning()) {
          UIUtil.dispatchAllInvocationEvents();
        }
        applied.clear();
        collected.clear();

        type("xxx"); // try to restart daemon

        start = System.currentTimeMillis();
        while (System.currentTimeMillis() < start + 5000) {
          assertEmpty(applied);  // it should not restart
          assertEmpty(collected);
          UIUtil.dispatchAllInvocationEvents();
        }
      }
      finally {
        runHeavyProcessing = false;
      }
    });
  }


  public void testModificationInsideCodeBlockDoesNotRehighlightWholeFile() {
    configureByText(JavaFileType.INSTANCE, "class X { int f = \"error\"; int f() { int gg<caret> = 11; return 0;} }");
    List<HighlightInfo> errors = highlightErrors();
    assertEquals(1, errors.size());
    assertEquals("Incompatible types. Found: 'java.lang.String', required: 'int'", errors.get(0).getDescription());

    errors.get(0).getHighlighter().dispose();

    errors = highlightErrors();
    assertEmpty(errors);

    type("23");
    errors = highlightErrors();
    assertEmpty(errors);

    myEditor.getCaretModel().moveToOffset(0);
    type("/* */");
    errors = highlightErrors();
    assertEquals(1, errors.size());
    assertEquals("Incompatible types. Found: 'java.lang.String', required: 'int'", errors.get(0).getDescription());
  }

  public void _testCaretMovementDoesNotRestartHighlighting() {
    configureByText(JavaFileType.INSTANCE, "class X { int f = \"error\"; int f() { int gg<caret> = 11; return 0;} }");

    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
    final DaemonCodeAnalyzerImpl di = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    final AtomicReference<ProgressIndicator> indicator = new AtomicReference<>();
    Runnable callbackWhileWaiting = () -> {
      if (indicator.get() == null) {
        indicator.set(di.getUpdateProgress());
      }
      assertSame(indicator.get(), di.getUpdateProgress());
      caretRight();
      if (getEditor().getCaretModel().getOffset() == getEditor().getDocument().getTextLength()-1) {
        getEditor().getCaretModel().moveToOffset(0);
      }
    };
    final List<HighlightInfo> errors = filter(
      di.runPasses(getFile(), getEditor().getDocument(), Collections.singletonList(textEditor), ArrayUtil.EMPTY_INT_ARRAY, false,
                   callbackWhileWaiting), HighlightSeverity.ERROR);

    assertEquals(1, errors.size());
    assertEquals("Incompatible types. Found: 'java.lang.String', required: 'int'", errors.get(0).getDescription());
  }


  public void testHighlightingDoesWaitForEmbarrassinglySlowExternalAnnotatorsToFinish() {
    configureByText(JavaFileType.INSTANCE, "class X { int f() { int gg<caret> = 11; return 0;} }");
    final AtomicBoolean run = new AtomicBoolean();
    final int SLEEP = 20000;
    ExternalAnnotator<Integer, Integer> annotator = new ExternalAnnotator<Integer, Integer>() {
      @Override
      public Integer collectInformation(@NotNull PsiFile file) {
        return 0;
      }

      @Override
      public Integer doAnnotate(final Integer collectedInfo) {
        TimeoutUtil.sleep(SLEEP);
        return 0;
      }

      @Override
      public void apply(@NotNull final PsiFile file, final Integer annotationResult, @NotNull final AnnotationHolder holder) {
        run.set(true);
      }
    };
    ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(JavaLanguage.INSTANCE, annotator);

    try {
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
    finally {
      ExternalLanguageAnnotators.INSTANCE.removeExplicitExtension(JavaLanguage.INSTANCE, annotator);
    }
  }

  public void testModificationInExcludedFileDoesNotCauseRehighlight() {
    VirtualFile excluded = configureByText(JavaFileType.INSTANCE, "class EEE { void f(){} }").getVirtualFile();
    PsiTestUtil.addExcludedRoot(myModule, excluded.getParent());

    configureByText(JavaFileType.INSTANCE, "class X { <caret> }");
    List<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);
    FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileStatusMap();
    TextRange scope = me.getFileDirtyScope(getEditor().getDocument(), Pass.UPDATE_ALL);
    assertNull(scope);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> ((PsiJavaFile)PsiManager.getInstance(myProject).findFile(excluded)).getClasses()[0].getMethods()[0].delete());

    UIUtil.dispatchAllInvocationEvents();
    scope = me.getFileDirtyScope(getEditor().getDocument(), Pass.UPDATE_ALL);
    assertNull(scope);
  }

  public void testModificationInWorkspaceXmlDoesNotCauseRehighlight() {
    configureByText(JavaFileType.INSTANCE, "class X { <caret> }");
    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    UtilKt.runInAllowSaveMode(() -> {
      application.saveAll(true);
      VirtualFile workspaceFile = Objects.requireNonNull(getProject().getWorkspaceFile());
      PsiFile excluded = ObjectUtils.assertNotNull(PsiManager.getInstance(getProject()).findFile(workspaceFile));

      List<HighlightInfo> errors = highlightErrors();
      assertEmpty(errors);
      FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileStatusMap();
      TextRange scope = me.getFileDirtyScope(getEditor().getDocument(), Pass.UPDATE_ALL);
      assertNull(scope);

      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        Document document = ObjectUtils.assertNotNull(PsiDocumentManager.getInstance(getProject()).getDocument(excluded));
        document.insertString(0, "<!-- dsfsd -->");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      UIUtil.dispatchAllInvocationEvents();
      scope = me.getFileDirtyScope(getEditor().getDocument(), Pass.UPDATE_ALL);
      assertNull(scope);
      return Unit.INSTANCE;
    });
  }

  public void testLightBulbDoesNotUpdateIntentionsInEDT() {
    final IntentionAction longLongUpdate = new AbstractIntentionAction() {
      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
      }

      @Nls
      @NotNull
      @Override
      public String getText() {
        return "LongAction";
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          throw new RuntimeException("Must not update actions in EDT");
        }
        return true;
      }
    };
    IntentionManager.getInstance().addAction(longLongUpdate);
    Disposer.register(getTestRootDisposable(), () -> IntentionManager.getInstance().unregisterIntention(longLongUpdate));
    configureByText(JavaFileType.INSTANCE, "class X { <caret>  }");
    makeEditorWindowVisible(new Point(0, 0), myEditor);
    doHighlighting();
    myDaemonCodeAnalyzer.restart();
    executeWithoutReparseDelay(() -> {
      for (int i = 0; i < 1000; i++) {
        caretRight();
        UIUtil.dispatchAllInvocationEvents();
        caretLeft();
        DaemonProgressIndicator updateProgress = myDaemonCodeAnalyzer.getUpdateProgress();
        while (myDaemonCodeAnalyzer.getUpdateProgress() == updateProgress) { // wait until daemon started
          UIUtil.dispatchAllInvocationEvents();
        }
        long start = System.currentTimeMillis();
        while (myDaemonCodeAnalyzer.isRunning() && System.currentTimeMillis() < start + 500) {
          UIUtil.dispatchAllInvocationEvents(); // wait for a bit more until ShowIntentionsPass.doApplyInformationToEditor() called
        }
      }
    });
  }

  private static void executeWithoutReparseDelay(@NotNull Runnable task) {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    int oldDelay = settings.AUTOREPARSE_DELAY;
    settings.AUTOREPARSE_DELAY = 0;
    try {
      task.run();
    }
    finally {
      settings.AUTOREPARSE_DELAY = oldDelay;
    }
  }

  public void testLightBulbIsHiddenWhenFixRangeIsCollapsed() {
    configureByText(StdFileTypes.JAVA, "class S { void foo() { boolean var; if (<selection>va<caret>r</selection>) {}} }");
    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);

    final Set<LightweightHint> visibleHints = ContainerUtil.newIdentityTroveSet();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
      @Override
      public void hintShown(final Project project, final LightweightHint hint, final int flags) {
        visibleHints.add(hint);
        hint.addHintListener(new HintListener() {
          @Override
          public void hintHidden(EventObject event) {
            visibleHints.remove(hint);
            hint.removeHintListener(this);
          }
        });
      }
    });

    highlightErrors();
    IntentionHintComponent lastHintBeforeDeletion = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(lastHintBeforeDeletion);

    delete(myEditor);
    highlightErrors();
    IntentionHintComponent lastHintAfterDeletion = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertSame(lastHintBeforeDeletion, lastHintAfterDeletion);

    assertEmpty(visibleHints);
  }

  public void testCodeFoldingPassRestartsOnRegionUnfolding() {
    executeWithoutReparseDelay(() -> {
      configureByText(StdFileTypes.JAVA, "class Foo {\n" +
                                         "    void m() {\n" +
                                         "\n" +
                                         "    }\n" +
                                         "}");
      CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(myEditor);
      waitForDaemon();
      EditorTestUtil.executeAction(myEditor, IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
      waitForDaemon();
      checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

      new WriteCommandAction<Void>(myProject) {
        @Override
        protected void run(@NotNull Result<Void> result) {
          myEditor.getDocument().insertString(0, "/*");
        }
      }.execute();
      waitForDaemon();
      checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

      EditorTestUtil.executeAction(myEditor, IdeActions.ACTION_EXPAND_ALL_REGIONS);
      waitForDaemon();
      checkFoldingState("[FoldRegion -(0:37), placeholder='/.../']");
    });
  }

  public void testChangingSettingsHasImmediateEffectOnOpenedEditor() {
    executeWithoutReparseDelay(() -> {
      configureByText(StdFileTypes.JAVA, "class C { \n" +
                                         "  void m() {\n" +
                                         "  } \n" +
                                         "}");
      CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(myEditor);
      waitForDaemon();
      checkFoldingState("[FoldRegion -(22:27), placeholder='{}']");

      JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
      boolean savedValue = settings.isCollapseMethods();
      try {
        settings.setCollapseMethods(true);
        CodeFoldingConfigurable.applyCodeFoldingSettingsChanges();
        waitForDaemon();
        checkFoldingState("[FoldRegion +(22:27), placeholder='{}']");
      }
      finally {
        settings.setCollapseMethods(savedValue);
      }
    });
  }

  private void checkFoldingState(String expected) {
    assertEquals(expected, Arrays.toString(myEditor.getFoldingModel().getAllFoldRegions()));
  }

  private void waitForDaemon() {
    long deadline = System.currentTimeMillis() + 60_000;
    while (!daemonIsWorkingOrPending()) {
      if (System.currentTimeMillis() > deadline) fail("Too long waiting for daemon to start");
      UIUtil.dispatchInvocationEvent();
    }
    while (daemonIsWorkingOrPending()) {
      if (System.currentTimeMillis() > deadline) fail("Too long waiting for daemon to finish");
      UIUtil.dispatchInvocationEvent();
    }
  }

  private boolean daemonIsWorkingOrPending() {
    return PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument()) || myDaemonCodeAnalyzer.isRunningOrPending();
  }

  public void testRehighlightInDebuggerExpressionFragment() {
    PsiExpressionCodeFragment fragment = JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("+ <caret>\"a\"", null,
                                    PsiType.getJavaLangObject(getPsiManager(), GlobalSearchScope.allScope(getProject())), true);
    myFile = fragment;
    Document document = ObjectUtils.assertNotNull(PsiDocumentManager.getInstance(getProject()).getDocument(fragment));
    myEditor = EditorFactory.getInstance().createEditor(document, getProject(), StdFileTypes.JAVA, false);

    ProperTextRange visibleRange = makeEditorWindowVisible(new Point(0, 0), myEditor);
    assertEquals(document.getTextLength(), visibleRange.getLength());

    try {
      final EditorInfo editorInfo = new EditorInfo(document.getText());

      final String newFileText = editorInfo.getNewFileText();
      ApplicationManager.getApplication().runWriteAction(() -> {
        if (!document.getText().equals(newFileText)) {
          document.setText(newFileText);
        }

        editorInfo.applyToEditor(myEditor);
      });

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();


      List<HighlightInfo> errors = highlightErrors();
      HighlightInfo error = assertOneElement(errors);
      assertEquals("Operator '+' cannot be applied to 'java.lang.String'", error.getDescription());

      type(" ");

      Collection<HighlightInfo> afterTyping = highlightErrors();
      HighlightInfo after = assertOneElement(afterTyping);
      assertEquals("Operator '+' cannot be applied to 'java.lang.String'", after.getDescription());
    }
    finally {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
  }

  public void testFileReload() throws Exception {
    VirtualFile file = createFile("a.java", "").getVirtualFile();
    Document document = getDocument(file);
    assertNotNull(document);

    FileStatusMap fileStatusMap = myDaemonCodeAnalyzer.getFileStatusMap();

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      PlatformTestUtil.tryGcSoftlyReachableObjects();
      assertNull(PsiDocumentManager.getInstance(getProject()).getCachedPsiFile(document));

      document.insertString(0, "class X { void foo() {}}");
      assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL));

      FileContentUtilCore.reparseFiles(file);
      assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL));

      findClass("X").getMethods()[0].delete();
      assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL));
    });
  }

  public void testAddRemoveHighlighterRaceInIncorrectAnnotatorsWhichUseFileRecursiveVisit() {
    Annotator annotator = new MyIncorrectlyRecursiveAnnotator();
    com.intellij.lang.Language java = StdFileTypes.JAVA.getLanguage();
    LanguageAnnotators.INSTANCE.addExplicitExtension(java, annotator);
    try {
      List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(java);
      assertTrue(list.toString(), list.contains(annotator));
      configureByText(StdFileTypes.JAVA, "class X {\n" +
                                         "  int foo(Object param) {\n" +
                                         "    if (param == this) return 1;\n" +
                                         "    return 0;\n" +
                                         "  }\n" +
                                         "}\n");
      ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
      assertEquals(getFile().getTextRange(), VisibleHighlightingPassFactory.calculateVisibleRange(getEditor()));

      assertEquals("XXX", assertOneElement(doHighlighting(HighlightSeverity.WARNING)).getDescription());

      for (int i=0; i<100; i++) {
        //System.out.println("i = " + i);
        DaemonCodeAnalyzer.getInstance(getProject()).restart();
        List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
        assertEquals("XXX", assertOneElement(infos).getDescription());
      }
    }
    finally {
      LanguageAnnotators.INSTANCE.removeExplicitExtension(java, annotator);
    }

    List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(java);
    assertFalse(list.toString(), list.contains(annotator));
  }

  public static class MyIncorrectlyRecursiveAnnotator implements Annotator {
    Random random = new Random();
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      if (psiElement instanceof PsiFile) {
        psiElement.accept(new JavaRecursiveElementWalkingVisitor(){
          @Override
          public void visitKeyword(PsiKeyword keyword) {
            if (Objects.equals(keyword.getText(), "this")) {
              holder.createAnnotation(HighlightSeverity.WARNING, keyword.getTextRange(), "XXX");
              TimeoutUtil.sleep(random.nextInt(100));
            }
          }
        });
      }
    }
  }

  public void testDumbQuickFixIsNoLongerVisibleAfterApplied() {
    registerInspection(new MyInspection());

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

  private List<IntentionAction> findStupidFixes() {
    return CodeInsightTestFixtureImpl.getAvailableIntentions(getEditor(), getFile())
      .stream().filter(f->f.getFamilyName().equals(new MyInspection.StupidQuickFixWhichDoesntCheckItsOwnApplicability().getFamilyName()))
      .collect(Collectors.toList());
  }

  private static class MyInspection extends LocalInspectionTool {
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
        public void visitIfStatement(PsiIfStatement statement) {
          if (statement.getElseBranch() != null) {
            PsiKeyword keyw = (PsiKeyword)statement.getChildren()[0];
            holder.registerProblem(keyw, "Dododo", new StupidQuickFixWhichDoesntCheckItsOwnApplicability());
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
}

