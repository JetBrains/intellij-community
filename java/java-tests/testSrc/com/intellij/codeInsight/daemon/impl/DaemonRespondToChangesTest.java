// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.application.options.editor.CodeFoldingConfigurable;
import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.EditorInfo;
import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.IntentionContainer;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspectionBase;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.configurationStore.StoreUtilKt;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.lang.LanguageFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
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
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.inline.InlineRefactoringActionHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.*;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import kotlin.Unit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class DaemonRespondToChangesTest extends DaemonAnalyzerTestCase {
  static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typing/";

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
  protected Sdk getTestProjectJdk() {
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
    ((EditorTrackerImpl)EditorTracker.getInstance(myProject)).setActiveEditors(Arrays.asList(editors));
  }

  @Override
  protected boolean doTestLineMarkers() {
    return true;
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

  private static void typeInAlienEditor(@NotNull Editor alienEditor, char c) {
    EditorActionManager.getInstance();
    TypedAction action = TypedAction.getInstance();
    DataContext dataContext = ((EditorEx)alienEditor).getDataContext();

    action.actionPerformed(alienEditor, c, dataContext);
  }

  public void testHighlightersUpdate() throws Exception {
    configureByFile(BASE_PATH + "HighlightersUpdate.java");
    Document document = getDocument(getFile());
    assertNotEmpty(highlightErrors());
    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.ERROR, getProject());
    assertEquals(1, errors.size());
    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertNull(dirty);

    type(' ');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertNotNull(dirty);
  }


  public void testNoPsiEventsAltogether() throws Exception {
    configureByFile(BASE_PATH + "HighlightersUpdate.java");
    Document document = getDocument(getFile());
    assertNotEmpty(highlightErrors());
    type(' ');
    backspace();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertEquals(getFile().getTextRange(), dirty); // have to rehighlight whole file in case no PSI events have come
  }

  public void testRenameClass() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
          
      }
    """);
    Document document = getDocument(getFile());
    assertEmpty(highlightErrors());
    PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];
    new RenameProcessor(myProject, psiClass, "Class2", false, false).run();
    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertEquals(getFile().getTextRange(), dirty);

    assertEmpty(highlightErrors());
    assertTrue(myDaemonCodeAnalyzer.isErrorAnalyzingFinished(getFile()));
  }


  public void testTypingSpace() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
          
      }
    """);
    Document document = getDocument(getFile());
    assertEmpty(highlightErrors());

    type("  ");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement elementAtCaret = myFile.findElementAt(myEditor.getCaretModel().getOffset());
    assertTrue(elementAtCaret instanceof PsiWhiteSpace);

    TextRange dirty = myDaemonCodeAnalyzer.getFileStatusMap().getFileDirtyScope(document, getFile(), Pass.UPDATE_ALL);
    assertEquals(elementAtCaret.getTextRange(), dirty);
    assertEmpty(highlightErrors());
    assertTrue(myDaemonCodeAnalyzer.isErrorAnalyzingFinished(getFile()));
  }


  public void testTypingSpaceInsideError() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass {
        {
          toString(0,<caret>0);
        }
      }
    """);
    assertOneElement(highlightErrors());

    for (int i = 0; i < 100; i++) {
      type(" ");
      assertOneElement(highlightErrors());
    }
  }


  public void testBackSpaceInsideError() {
    configureByText(JavaFileType.INSTANCE, """
      class E {
           void fff() {
               int i = <caret>
           }
       }
    """);
    assertOneElement(highlightErrors());

    backspace();
    assertOneElement(highlightErrors());
  }

  public void testUnusedFieldUpdate() {
    configureByText(JavaFileType.INSTANCE, """
     class Unused {
       private int ffff;
       void foo(int p) {
         if (p==0) return;
         <caret>
       }
     }
    """);
    Document document = getDocument(getFile());
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(1, infos.size());
    assertEquals("Private field 'ffff' is never used", infos.get(0).getDescription());

    type("  foo(ffff++);");
    assertEmpty(highlightErrors());

    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, getProject());
    assertEquals(0, errors.size());
  }

  public void testUnusedMethodUpdate() {
    configureByText(JavaFileType.INSTANCE, """
      class X {
          static void ffff() {}
          public static void main(String[] args){
              for (int i=0; i<1000;i++) {
                  System.out.println(i);
                  <caret>ffff();
              }
          }
      }""");
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
    WriteCommandAction.runWriteCommandAction(getProject(), () -> EditorModificationUtilEx.deleteSelectedText(getEditor()));
    type("  text");

    List<HighlightInfo> errors = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(getFile().getText(), errors);
  }

  public void testDaemonIgnoresNonPhysicalEditor() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
          
      }
    """);
    assertEmpty(highlightErrors());

    EditorFactory editorFactory = EditorFactory.getInstance();
    Document consoleDoc = editorFactory.createDocument("my console blah");
    Editor consoleEditor = editorFactory.createEditor(consoleDoc);

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


  public void testDaemonIgnoresConsoleActivities() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
          
      }
    """);

    assertEmpty(highlightErrors());

    ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(getProject()).getConsole();

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

  private void checkDaemonReaction(boolean mustCancelItself, @NotNull Runnable action) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    highlightErrors();
    myDaemonCodeAnalyzer.waitForTermination();
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());

    AtomicBoolean run = new AtomicBoolean();
    Disposable disposable = Disposer.newDisposable();
    AtomicReference<RuntimeException> stopDaemonReason = new AtomicReference<>();
    StorageUtilKt.setDEBUG_LOG("");
    getProject().getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            new DaemonCodeAnalyzer.DaemonListener() {
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
          myDaemonCodeAnalyzer.runPasses(getFile(), getDocument(getFile()), textEditor, toIgnore, true, callbackWhileWaiting);
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



  public void testOverriddenMethodMarkers() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    assertEmpty(highlightErrors());

    Document document = getEditor().getDocument();
    List<LineMarkerInfo<?>> markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertEquals(3, markers.size());

    type("//xxxx");

    assertEmpty(highlightErrors());
    markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertEquals(3, markers.size());
  }


  public void testOverriddenMethodMarkersDoNotClearedByChangingWhitespaceNearby() throws Exception {
    configureByFile(BASE_PATH + "OverriddenMethodMarkers.java");
    assertEmpty(highlightErrors());

    Document document = getEditor().getDocument();
    List<LineMarkerInfo<?>> markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertEquals(markers.toString(), 3, markers.size());

    PsiElement element = ((PsiJavaFile)myFile).getClasses()[0].findMethodsByName("f", false)[0].getReturnTypeElement().getNextSibling();
    assertEquals("   ", element.getText());
    getEditor().getCaretModel().moveToOffset(element.getTextOffset() + 1);
    type(" ");

    assertEmpty(highlightErrors());
    markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertEquals(markers.toString(), 3, markers.size());
  }


  public void testChangeXmlIncludeLeadsToRehighlight() {
    LanguageFilter[] extensions = XMLLanguage.INSTANCE.getLanguageExtensions();
    for (LanguageFilter extension : extensions) {
      XMLLanguage.INSTANCE.unregisterLanguageExtension(extension);
    }

    String location = getTestName(false) + ".xsd";
    final String url = "http://myschema/";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());

    configureByFiles(null, BASE_PATH + getTestName(false) + ".xml", BASE_PATH + getTestName(false) + ".xsd");

    assertEmpty(highlightErrors());

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
    delete(Objects.requireNonNull(schemaEditor));

    assertNotEmpty(highlightErrors());

    for (LanguageFilter extension : extensions) {
      XMLLanguage.INSTANCE.registerLanguageExtension(extension);
    }
  }


  public void testRehighlightInnerBlockAfterInline() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    HighlightInfo error = assertOneElement(highlightErrors());
    assertEquals("Variable 'e' is already defined in the scope", error.getDescription());
    PsiElement element = getFile().findElementAt(getEditor().getCaretModel().getOffset()).getParent();

    DataContext dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT, element, ((EditorEx)getEditor()).getDataContext());
    new InlineRefactoringActionHandler().invoke(getProject(), getEditor(), getFile(), dataContext);

    assertEmpty(highlightErrors());
  }


  public void testRangeMarkersDoNotGetAddedOrRemovedWhenUserIsJustTypingInsideHighlightedRegionAndEspeciallyInsideInjectedFragmentsWhichAreColoredGreenAndUsersComplainEndlesslyThatEditorFlickersThere() {
    configureByText(JavaFileType.INSTANCE, """
      class S { int f() {
          return <caret>hashCode();
      }}""");

    Collection<HighlightInfo> infos = doHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertEquals(3, infos.size());

    AtomicInteger count = new AtomicInteger();
    MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(getFile()), getProject(), true);
    modelEx.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        count.incrementAndGet();
      }

      @Override
      public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
        count.incrementAndGet();
      }
    });

    type(' ');
    assertEmpty(highlightErrors());

    assertEquals(0, count.get());
  }

  public void testLineMarkersReuse() throws Throwable {
    configureByFile(BASE_PATH + "LineMarkerChange.java");

    assertEmpty(highlightErrors());

    List<LineMarkerInfo<?>> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
    assertSize(5, lineMarkers);

    type('X');

    Collection<String> changed = new ArrayList<>();
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
        EdtInvocationManager.invokeLaterIfNeeded(() -> {
          List<LineMarkerInfo<?>> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
          if (ContainerUtil.find(lineMarkers, lm -> lm.highlighter == highlighter) != null) {
            changed.add(highlighter + ": \n" + reason);
          } // else not line marker
        });
      }
    });

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    List<HighlightInfo> infosAfter = CodeInsightTestFixtureImpl.instantiateAndRun(myFile, myEditor, new int[]{/*Pass.UPDATE_ALL, Pass.LOCAL_INSPECTIONS*/}, false);
    assertNotEmpty(filter(infosAfter, HighlightSeverity.ERROR));
    UIUtil.dispatchAllInvocationEvents();
    assertEmpty(changed);
    List<LineMarkerInfo<?>> lineMarkersAfter = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
    assertEquals(lineMarkersAfter.size(), lineMarkers.size());
  }

  public void testLineMarkersDoNotBlinkOnBackSpaceRightBeforeMethodIdentifier() {
    configureByText(JavaFileType.INSTANCE, """
      package x;\s
      class  <caret>ToRun{
        public static void main(String[] args) {
        }
      }""");

    assertEmpty(highlightErrors());

    List<LineMarkerInfo<?>> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
    assertSize(2, lineMarkers);

    backspace();

    Collection<String> changed = Collections.synchronizedList(new ArrayList<>());
    MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(getFile()), getProject(), true);
    modelEx.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        changed(highlighter, "after added");
      }

      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        changed(highlighter, "before removed");
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleChanged) {
        changed(highlighter, "changed");
      }

      private void changed(@NotNull RangeHighlighterEx highlighter, @NotNull String reason) {
        if (highlighter.getTargetArea() != HighlighterTargetArea.LINES_IN_RANGE) return; // not line marker
        List<LineMarkerInfo<?>> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
        if (ContainerUtil.find(lineMarkers, lm -> lm.highlighter == highlighter) != null) {
          changed.add(highlighter + ": \n" + ExceptionUtil.getThrowableText(new Throwable(reason)));
        } // else not line marker
      }
    });

    assertEmpty(highlightErrors());
    UIUtil.dispatchAllInvocationEvents();
    assertSize(2, DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject()));

    assertEmpty(changed);
  }

  public void testTypeParametersMustNotBlinkWhenTypingInsideClass() {
    configureByText(JavaFileType.INSTANCE, """
      package x;
      abstract class ToRun<TTTTTTTTTTTTTTT> implements Comparable<TTTTTTTTTTTTTTT> {
        private ToRun<TTTTTTTTTTTTTTT> delegate;
        <caret>
       \s
      }""");

    assertEmpty(highlightErrors());

    MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(getFile()), getProject(), true);
    modelEx.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        if (highlighter.getTextRange().substring(highlighter.getDocument().getText()).equals("TTTTTTTTTTTTTTT")) {
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
    configureByText(JavaFileType.INSTANCE, """
      class A {
          String cons;
          void foo() {
              String local = null;
              <selection>cons</selection>.substring(1);    }
          public static void main(String[] args) {
              new A().foo();
          }}""");
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
    configureByText(JavaFileType.INSTANCE, """
      package x;\s
      class ClassA {
          static <T> void sayHello(Class<? extends T> msg) {}
      }
      class ClassB extends ClassA {
          static <T extends String> void sayHello(Class<? extends T> msg) {<caret>
          }
      }
      """);

    assertOneElement(highlightErrors());
    type("//my comment inside method body, so class modifier won't be visited");
    assertOneElement(highlightErrors());
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  public void testLineMarkersClearWhenTypingAtTheEndOfPsiComment() {
    configureByText(JavaFileType.INSTANCE, "class S {\n//ddd<caret>\n}");
    StringBuffer log = new StringBuffer();
    LineMarkerProvider provider = element -> {
      String msg = "provider.getLineMarkerInfo(" + element + ") called\n";
      LineMarkerInfo<PsiComment> info = null;
      if (element instanceof PsiComment) {
        info = new LineMarkerInfo<>((PsiComment)element, element.getTextRange(), null, null, null, GutterIconRenderer.Alignment.LEFT);
        msg += " provider info: "+info + "\n";
      }
      log.append(msg);
      return info;
    };
    LineMarkerProviders.getInstance().addExplicitExtension(JavaLanguage.INSTANCE, provider, getTestRootDisposable());
    myDaemonCodeAnalyzer.restart();
    try {
      TextRange range = Objects.requireNonNull(FileStatusMap.getDirtyTextRange(myEditor.getDocument(), myFile, Pass.UPDATE_ALL));
      log.append("FileStatusMap.getDirtyTextRange: " + range+"\n");
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(getFile(), range.getStartOffset(), range.getEndOffset());
      log.append("CollectHighlightsUtil.getElementsInRange: " + range + ": " + elements.size() +" elements : "+ elements+"\n");
      List<HighlightInfo> infos = doHighlighting();
      log.append(" File text: '" + getFile().getText() + "'\n");
      log.append("infos: " + infos + "\n");
      assertEmpty(filter(infos,HighlightSeverity.ERROR));

      List<LineMarkerInfo<?>> lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject());
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
    configureByText(JavaFileType.INSTANCE, """
      class S {  int f() {
          return asfsdfsdfsd<caret>;
      }}""");

    HighlightInfo error = assertOneElement(highlightErrors());
    assertSame(HighlightInfoType.WRONG_REF, error.type);

    Document document = getDocument(getFile());

    type("xxx");

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightInfoType.SYMBOL_TYPE_SEVERITY, getProject());
    for (HighlightInfo info : infos) {
      assertNotSame(HighlightInfoType.WRONG_REF, info.type);
    }

    HighlightInfo error2 = assertOneElement(highlightErrors());
    assertSame(HighlightInfoType.WRONG_REF, error2.type);
  }


  public void testQuickFixRemainsAvailableAfterAnotherFixHasBeenAppliedInTheSameCodeBlockBefore() throws Exception {
    configureByFile(BASE_PATH + "QuickFixes.java");

    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean old = settings.isNextErrorActionGoesToErrorsFirst();
    settings.setNextErrorActionGoesToErrorsFirst(true);

    try {
      Collection<HighlightInfo> errors = highlightErrors();
      assertEquals(3, errors.size());
      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());

      List<IntentionAction> fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
      IntentionAction fix = assertContainsOneOf(fixes, "Delete catch for 'java.io.IOException'");

      IntentionAction finalFix = fix;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> finalFix.invoke(getProject(), getEditor(), getFile()));

      errors = highlightErrors();
      assertEquals(2, errors.size());

      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());
      fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
      fix = assertContainsOneOf(fixes, "Delete catch for 'java.io.IOException'");

      IntentionAction finalFix1 = fix;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> finalFix1.invoke(getProject(), getEditor(), getFile()));

      assertOneElement(highlightErrors());

      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());
      fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
      fix = assertContainsOneOf(fixes, "Delete catch for 'java.io.IOException'");

      IntentionAction finalFix2 = fix;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> finalFix2.invoke(getProject(), getEditor(), getFile()));

      assertEmpty(highlightErrors());
    }
    finally {
      settings.setNextErrorActionGoesToErrorsFirst(old);
    }
  }

  private static IntentionAction assertContainsOneOf(@NotNull Collection<? extends IntentionAction> collection, @NotNull String text) {
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
    configureByText(JavaFileType.INSTANCE, "class S { void ffffff() {fff<caret>fff();}}");

    assertEmpty(highlightErrors());
    MarkupModel markup = DocumentMarkupModel.forDocument(myEditor.getDocument(), getProject(), true);
    TextRange[] highlightersBefore = getHighlightersTextRange(markup);

    type("%%%%");
    assertNotEmpty(highlightErrors());
    backspace();
    backspace();
    backspace();
    backspace();
    assertEmpty(highlightErrors());

    TextRange[] highlightersAfter = getHighlightersTextRange(markup);

    assertEquals(highlightersBefore.length, highlightersAfter.length);
    for (int i = 0; i < highlightersBefore.length; i++) {
      TextRange before = highlightersBefore[i];
      TextRange after = highlightersAfter[i];
      assertEquals(before.getStartOffset(), after.getStartOffset());
      assertEquals(before.getEndOffset(), after.getEndOffset());
    }
  }

  private static TextRange @NotNull [] getHighlightersTextRange(@NotNull MarkupModel markup) {
    RangeHighlighter[] highlighters = markup.getAllHighlighters();

    TextRange[] result = new TextRange[highlighters.length];
    for (int i = 0; i < highlighters.length; i++) {
      result[i] = ProperTextRange.create(highlighters[i]);
    }
    return orderByHashCode(result); // markup.getAllHighlighters returns unordered array
  }

  private static <T extends Segment> T @NotNull [] orderByHashCode(T @NotNull [] highlighters) {
    Arrays.sort(highlighters, (o1, o2) -> o2.hashCode() - o1.hashCode());
    return highlighters;
  }

  public void testFileStatusMapDirtyCachingWorks() {
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(false); // to prevent auto-start highlighting
    UIUtil.dispatchAllInvocationEvents();
    configureByText(JavaFileType.INSTANCE, "class <caret>S { int ffffff =  0;}");
    UIUtil.dispatchAllInvocationEvents();

    int[] creation = {0};
    class Fac implements TextEditorHighlightingPassFactory {
      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
        TextRange textRange = FileStatusMap.getDirtyTextRange(editor.getDocument(), file, Pass.UPDATE_ALL);
        if (textRange == null) return null;
        return new MyPass(myProject);
      }

      final class MyPass extends TextEditorHighlightingPass {
        private MyPass(Project project) {
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
    registrar.registerTextEditorHighlightingPass(new Fac(), null, null, false, -1);
    assertEmpty(highlightErrors());
    assertEquals(1, creation[0]);

    //cached
    assertEmpty(highlightErrors());
    assertEquals(1, creation[0]);
    assertEmpty(highlightErrors());
    assertEquals(1, creation[0]);

    type(' ');
    assertEmpty(highlightErrors());
    assertEquals(2, creation[0]);
    assertEmpty(highlightErrors());
    assertEquals(2, creation[0]);
    assertEmpty(highlightErrors());
    assertEquals(2, creation[0]);
  }


  public void testDefensivelyDirtyFlagDoesNotClearPrematurely() {
    class Fac implements TextEditorHighlightingPassFactory {
      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
        return null;
      }
    }
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(getProject());
    registrar.registerTextEditorHighlightingPass(new Fac(), null, null, false, -1);

    configureByText(JavaFileType.INSTANCE, "@Deprecated<caret> class S { } ");

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


  public void testModificationInsideCodeBlockDoesNotAffectErrorMarkersOutside() {
    configureByText(JavaFileType.INSTANCE, """
      class SSSSS {
          public static void suite() {
              <caret>
              new Runnable() {
                  public void run() {
            
                  }
              };
          }
      
      """);
    HighlightInfo error = assertOneElement(highlightErrors());
    assertEquals("'}' expected", error.getDescription());

    type("//comment");
    HighlightInfo error2 = assertOneElement(highlightErrors());
    assertEquals("'}' expected", error2.getDescription());
  }

  public void testErrorMarkerAtTheEndOfTheFile() {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      try {
        configureByFile(BASE_PATH + "ErrorMarkAtEnd.java");
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }, "Cc", this);
    EditorTestUtil.setEditorVisibleSizeInPixels(getEditor(), 1000, 1000);
    assertEmpty(highlightErrors());
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      Document document = getEditor().getDocument();
      int offset = getEditor().getCaretModel().getOffset();
      while (offset < document.getTextLength()) {
        int i = StringUtil.indexOf(document.getText(), '}', offset, document.getTextLength());
        if (i == -1) break;
        getEditor().getCaretModel().moveToOffset(i);
        delete(getEditor());
      }
    }, "My", this);

    List<HighlightInfo> errs = highlightErrors();
    assertEquals(2, errs.size());
    assertEquals("'}' expected", errs.get(0).getDescription());

    undo();
    assertEmpty(highlightErrors());
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

    assertNotEmpty(highlightErrors());

    IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));

    type("x");
    assertNotEmpty(highlightErrors());
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

    assertNotEmpty(highlightErrors());

    IntentionHintComponent hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponent);
    assertFalse(hintComponent.isDisposed());
    assertNotNull(hintComponent.getComponentHint());
    assertTrue(shown.contains(hintComponent.getComponentHint()));
    assertTrue(hintComponent.hasVisibleLightBulbOrPopup());

    CommandProcessor.getInstance().executeCommand(getProject(), () -> EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_EDITOR_ESCAPE, true), "", null, getEditor().getDocument());

    assertNotEmpty(highlightErrors());
    hintComponent = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNull(hintComponent);

    // the bulb must reappear when the caret moved
    caretLeft();
    assertNotEmpty(highlightErrors());
    IntentionHintComponent hintComponentAfter = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(hintComponentAfter);
    assertFalse(hintComponentAfter.isDisposed());
    assertNotNull(hintComponentAfter.getComponentHint());
    assertTrue(shown.contains(hintComponentAfter.getComponentHint()));
    assertTrue(hintComponentAfter.hasVisibleLightBulbOrPopup());
  }

  // todo - StoreUtil.saveDocumentsAndProjectsAndApp cannot save in EDT. If it is called in EDT,
  //  in this case, task is done under a modal progress, so, no idea how to fix the test, except executing it not in EDT (as it should be)
  public void _testDaemonIgnoresFrameDeactivation() {
    // return default value to avoid unnecessary save
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    String text = "class S { ArrayList<caret>XXX x;}";
    configureByText(JavaFileType.INSTANCE, text);
    assertNotEmpty(highlightErrors());

    GeneralSettings settings = GeneralSettings.getInstance();
    boolean frameSave = settings.isSaveOnFrameDeactivation();
    settings.setSaveOnFrameDeactivation(true);
    StoreUtilKt.runInAllowSaveMode(true, () -> {
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
    configureByText(JavaFileType.INSTANCE, "class X { static int sss; public int f() { return this.<caret>sss; }}");

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    List<HighlightInfo> warns = doHighlighting(HighlightSeverity.WARNING);
    assertOneElement(warns);
    Editor editor = getEditor();
    List<HighlightInfo.IntentionActionDescriptor> actions =
      ShowIntentionsPass.getAvailableFixes(editor, getFile(), -1, ((EditorEx)editor).getExpectedCaretOffset());
    HighlightInfo.IntentionActionDescriptor descriptor = assertOneElement(actions);
    CodeInsightTestFixtureImpl.invokeIntention(descriptor.getAction(), getFile(), getEditor());

    assertEmpty(highlightErrors());
    assertEmpty(ShowIntentionsPass.getAvailableFixes(editor, getFile(), -1, ((EditorEx)editor).getExpectedCaretOffset()));
  }


  public void testApplyErrorInTheMiddle() {
    String text = "class <caret>X { " + """

                                               {
                                           //    String x = "<zzzzzzzzzz/>";
                                               }""".repeat(100) +
                  "\n}";
    configureByText(JavaFileType.INSTANCE, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    assertEmpty(highlightErrors());

    type("//");
    List<HighlightInfo> errors = highlightErrors();
    assertEquals(2, errors.size());

    backspace();
    backspace();

    assertEmpty(highlightErrors());
  }


  public void testErrorInTheEndOutsideVisibleArea() {
    String text = "<xml> \n" + StringUtil.repeatSymbol('\n', 1000) + "</xml>\nxxxxx<caret>";
    configureByText(XmlFileType.INSTANCE, text);

    ProperTextRange visibleRange = makeEditorWindowVisible(new Point(0, 1000), myEditor);
    assertTrue(visibleRange.getStartOffset() > 0);

    HighlightInfo info = assertOneElement(highlightErrors());
    assertEquals("Top level element is not completed", info.getDescription());

    type("xxx");
    info = assertOneElement(highlightErrors());
    assertEquals("Top level element is not completed", info.getDescription());
  }

  @NotNull
  public static ProperTextRange makeEditorWindowVisible(@NotNull Point viewPosition, @NotNull Editor editor) {
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    int offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(viewPosition));
    VisibleHighlightingPassFactory.setVisibleRangeForHeadlessMode(editor, new ProperTextRange(offset, offset));
    return editor.calculateVisibleRange();
  }

  static void makeWholeEditorWindowVisible(@NotNull EditorImpl editor) {
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);
    VisibleHighlightingPassFactory.setVisibleRangeForHeadlessMode(editor, new ProperTextRange(0, editor.getDocument().getTextLength()));
  }


  public void testEnterInCodeBlock() {
    String text = """
      class LQF {
          int wwwwwwwwwwww;
          public void main() {<caret>
              wwwwwwwwwwww = 1;
          }
      }""";
    configureByText(JavaFileType.INSTANCE, text);

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
    String text = """
      class LQF {
          public void main() {
              int wwwwwwwwwwww = 1<caret>
          }
      }""";
    configureByText(JavaFileType.INSTANCE, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);

    assertOneElement(highlightErrors());

    type(';');
    assertEmpty(highlightErrors());
  }



  public void testCancelsItSelfOnTypingInAlienProject() throws Throwable {
    String body = StringUtil.repeat("\"String field = null;\"\n", 1000);
    configureByText(JavaFileType.INSTANCE, "class X{ void f() {" + body + "<caret>\n} }");

    Project alienProject = PlatformTestUtil.loadAndOpenProject(createTempDirectory().toPath().resolve("alien.ipr"), getTestRootDisposable());
    DaemonProgressIndicator.setDebug(true);

    try {
      Module alienModule = doCreateRealModuleIn("x", alienProject, getModuleType());
      VirtualFile alienRoot = createTestProjectStructure(alienModule, null, true, getTempDir());
      PsiDocumentManager.getInstance(alienProject).commitAllDocuments();
      OpenFileDescriptor alienDescriptor = WriteAction.compute(() -> {
        VirtualFile alienFile = alienRoot.createChildData(this, "AlienFile.java");
        setFileText(alienFile, "class Alien { }");
        return new OpenFileDescriptor(alienProject, alienFile);
      });

      FileEditorManager fe = FileEditorManager.getInstance(alienProject);
      Editor alienEditor = Objects.requireNonNull(fe.openTextEditor(alienDescriptor, false));
      ((EditorImpl)alienEditor).setCaretActive();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      PsiDocumentManager.getInstance(alienProject).commitAllDocuments();

      // start daemon in the main project. should check for its cancel when typing in alien
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
      AtomicBoolean checked = new AtomicBoolean();
      Runnable callbackWhileWaiting = () -> {
        if (checked.getAndSet(true)) return;
        typeInAlienEditor(alienEditor, 'x');
      };
      myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), textEditor, ArrayUtilRt.EMPTY_INT_ARRAY, true, callbackWhileWaiting);
    }
    catch (ProcessCanceledException ignored) {
      return;
    }
    fail("must throw PCE");
  }

  public void testPasteInAnonymousCodeBlock() {
    configureByText(JavaFileType.INSTANCE, """
      class X{ void f() {     int x=0;x++;
          Runnable r = new Runnable() { public void run() {
       <caret>
          }};
          <selection>int y = x;</selection>
      \s
      } }""");
    assertEmpty(highlightErrors());
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_EDITOR_COPY);
    assertEquals("int y = x;", getEditor().getSelectionModel().getSelectedText());
    getEditor().getSelectionModel().removeSelection();
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_EDITOR_PASTE);
    assertOneElement(highlightErrors());
  }

  public void testPostHighlightingPassRunsOnEveryPsiModification() throws Exception {
    @Language("JAVA")
    String xText = "public class X { public static void ffffffffffffff(){} }";
    PsiFile x = createFile("X.java", xText);
    PsiFile use = createFile("Use.java", "public class Use { { <caret>X.ffffffffffffff(); } }");
    configureByExistingFile(use.getVirtualFile());

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    HighlightDisplayKey myDeadCodeKey = HighlightDisplayKey.findOrRegister(UnusedDeclarationInspectionBase.SHORT_NAME,
                                                                           UnusedDeclarationInspectionBase.getDisplayNameText(), UnusedDeclarationInspectionBase.SHORT_NAME);
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
    configureByText(JavaFileType.INSTANCE, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(100, 100);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ((EditorImpl)myEditor).getScrollPane().getViewport().setViewPosition(new Point(0, 0));
    ((EditorImpl)myEditor).getScrollPane().getViewport().setExtentSize(new Dimension(100, 100000));
    @NotNull Editor editor = getEditor();
    ProperTextRange visibleRange = editor.calculateVisibleRange();
    assertTrue(visibleRange.getLength() > 0);
    Document document = myEditor.getDocument();
    assertTrue(visibleRange.getLength() < document.getTextLength());

    HighlightInfo info = assertOneElement(highlightErrors());
    final String errorDescription = "Incompatible types. Found: 'null', required: 'int'";
    assertEquals(errorDescription, info.getDescription());

    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myProject, false);
    AtomicBoolean errorRemoved = new AtomicBoolean();

    model.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
        HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
        if (info == null) return;
        String description = info.getDescription();
        if (errorDescription.equals(description)) {
          errorRemoved.set(true);

          List<ProgressableTextEditorHighlightingPass> passes = myDaemonCodeAnalyzer.getPassesToShowProgressFor(document);
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

    assertEmpty(highlightErrors());
    assertTrue(errorRemoved.get());
  }

  public void testDaemonWorksForDefaultProjectSinceItIsNeededInSettingsDialogForSomeReason() {
    assertNotNull(DaemonCodeAnalyzer.getInstance(ProjectManager.getInstance().getDefaultProject()));
  }

  public void testChangeEventsAreNotAlwaysGeneric() {
    String body = """
      class X {
      <caret>    @org.PPP
          void dtoArrayDouble() {
          }
      }""";
    configureByText(JavaFileType.INSTANCE, body);
    makeEditorWindowVisible(new Point(), myEditor);

    assertNotEmpty(highlightErrors());

    type("//");
    assertEmpty(highlightErrors());

    backspace();
    backspace();
    assertNotEmpty(highlightErrors());
  }

  public void testInterruptOnTyping() throws Throwable {
    @NonNls String filePath = "/psi/resolve/Thinlet.java";
    configureByFile(filePath);
    assertEmpty(highlightErrors());

    myDaemonCodeAnalyzer.restart();
    try {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      PsiFile file = getFile();
      Editor editor = getEditor();
      Project project = file.getProject();
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
      Runnable callbackWhileWaiting = () -> type(' ');
      myDaemonCodeAnalyzer.runPasses(file, editor.getDocument(), textEditor, ArrayUtilRt.EMPTY_INT_ARRAY, true, callbackWhileWaiting);
    }
    catch (ProcessCanceledException ignored) {
      return;
    }
    fail("PCE must have been thrown");
  }

  public void testTypingInsideCodeBlockDoesntLeadToCatastrophicUnusedEverything_Stress() throws Throwable {
    InspectionProfileImpl profile = InspectionProfileManager.getInstance(getProject()).getCurrentProfile();
    profile.disableAllTools(getProject());
    @NonNls String filePath = "/psi/resolve/Thinlet.java";
    configureByFile(filePath);

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiFile file = getFile();
    Project project = file.getProject();
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
    List<HighlightInfo> errors = doHighlighting(HighlightSeverity.ERROR);
    assertEmpty(errors);
    List<HighlightInfo> initialWarnings = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(initialWarnings);
    int N_BLOCKS = codeBlocks(file).size();
    assertTrue("codeblocks :"+N_BLOCKS, N_BLOCKS > 1000);
    Random random = new Random();
    int N = 10;
    // try with both serialized and not-serialized passes
    myDaemonCodeAnalyzer.serializeCodeInsightPasses(false);
    for (int i=0; i<N*2; i++) {
      PsiCodeBlock block = codeBlocks(file).get(random.nextInt(N_BLOCKS));
      getEditor().getCaretModel().moveToOffset(block.getLBrace().getTextOffset() + 1);
      type("\n/*xxx*/");
      List<HighlightInfo> warnings = doHighlighting(HighlightSeverity.WARNING);
      if (!warnings.isEmpty()) {
        System.out.println("\n-----\n"+getEditor().getDocument().getText()+"\n--------\n");
      }
      assertEmpty(warnings);
      if (i == N) {
        // repeat the same steps with serialized passes
        myDaemonCodeAnalyzer.serializeCodeInsightPasses(true);
      }
    }
  }

  @NotNull
  private List<PsiCodeBlock> codeBlocks(@NotNull PsiFile file) {
    List<PsiCodeBlock> blocks = new ArrayList<>();
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitCodeBlock(@NotNull PsiCodeBlock block) {
        blocks.add(block);
        super.visitCodeBlock(block);
      }
    });
    return blocks;
  }

  public void testCodeFoldingInSplittedWindowAppliesToAllEditors() {
    Set<Editor> applied = ConcurrentCollectionFactory.createConcurrentSet();
    Set<Editor> collected = ConcurrentCollectionFactory.createConcurrentSet();
    registerFakePass(applied, collected);

    configureByText(PlainTextFileType.INSTANCE, "");
    Editor editor1 = getEditor();
    Editor editor2 = EditorFactory.getInstance().createEditor(editor1.getDocument(),getProject());
    Disposer.register(getProject(), () -> EditorFactory.getInstance().releaseEditor(editor2));
    TextEditor textEditor1 = new PsiAwareTextEditorProvider().getTextEditor(editor1);
    TextEditor textEditor2 = new PsiAwareTextEditorProvider().getTextEditor(editor2);

    myDaemonCodeAnalyzer.runPasses(myFile, editor1.getDocument(), textEditor1, new int[0], false, null);
    myDaemonCodeAnalyzer.runPasses(myFile, editor1.getDocument(), textEditor2, new int[0], false, null);
    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(editor1.getDocument(), null, myProject);
    assertEmpty(errors);

    assertEquals(collected, ContainerUtil.newHashSet(editor1, editor2));
    assertEquals(applied, ContainerUtil.newHashSet(editor1, editor2));
  }

  public void testHighlightingInSplittedWindowFinishesEventually() {
    myDaemonCodeAnalyzer.serializeCodeInsightPasses(true); // reproduced only for serialized passes
    try {
      Collection<Editor> applied = ContainerUtil.createEmptyCOWList();
      Collection<Editor> collected = ContainerUtil.createEmptyCOWList();
      registerFakePass(applied, collected);

      @Language("JAVA")
      String text = "class X {" + "\n".repeat(1000) +
                    "}";
      configureByText(JavaFileType.INSTANCE, text);
      Editor editor1 = getEditor();
      Editor editor2 = EditorFactory.getInstance().createEditor(editor1.getDocument(),getProject());
      Disposer.register(getProject(), () -> EditorFactory.getInstance().releaseEditor(editor2));
      TextEditor textEditor1 = new PsiAwareTextEditorProvider().getTextEditor(editor1);
      TextEditor textEditor2 = new PsiAwareTextEditorProvider().getTextEditor(editor2);
      setActiveEditors(editor1, editor2);

      myDaemonCodeAnalyzer.runPasses(myFile, editor1.getDocument(), textEditor1, new int[0], false, null);
      myDaemonCodeAnalyzer.runPasses(myFile, editor1.getDocument(), textEditor2, new int[0], false, null);

      assertSameElements(collected, Arrays.asList(editor1, editor2));
      assertSameElements(applied, Arrays.asList(editor1, editor2));

      applied.clear();
      collected.clear();
      setActiveEditors(editor1, editor2);
      type("/* xxx */");
      waitForDaemon(myProject, myEditor.getDocument());

      assertSameElements(collected, Arrays.asList(editor1, editor2));
      assertSameElements(applied, Arrays.asList(editor1, editor2));
    }
    finally {
      myDaemonCodeAnalyzer.serializeCodeInsightPasses(false);
    }
  }

  private void registerFakePass(@NotNull Collection<? super Editor> applied, @NotNull Collection<? super Editor> collected) {
    class Fac implements TextEditorHighlightingPassFactory {
      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
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
    registrar.registerTextEditorHighlightingPass(new Fac(), null, null, false, -1);
  }

  private volatile boolean runHeavyProcessing;
  public void testDaemonDisablesItselfDuringHeavyProcessing() {
    runWithReparseDelay(0, () -> {
      runHeavyProcessing = false;
      try {
        Set<Editor> applied = Collections.synchronizedSet(new HashSet<>());
        Set<Editor> collected = Collections.synchronizedSet(new HashSet<>());
        registerFakePass(applied, collected);

        configureByText(PlainTextFileType.INSTANCE, "");
        Editor editor = getEditor();
        EditorTracker editorTracker = EditorTracker.getInstance(myProject);
        setActiveEditors(editor);
        while (HeavyProcessLatch.INSTANCE.isRunning()) {
          UIUtil.dispatchAllInvocationEvents();
        }
        type("xxx"); // restart daemon
        assertTrue(editorTracker.getActiveEditors().contains(editor));
        assertSame(editor, FileEditorManager.getInstance(myProject).getSelectedTextEditor());


        // wait for the first pass to complete
        long start = System.currentTimeMillis();
        while (myDaemonCodeAnalyzer.isRunning() || !applied.contains(editor)) {
          UIUtil.dispatchAllInvocationEvents();
          if (System.currentTimeMillis() - start > 1000000) {
            fail("Too long waiting for daemon");
          }
        }

        runHeavyProcessing = true;
        ApplicationManager.getApplication().executeOnPooledThread(() ->
          HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Processing, "my own heavy op", ()-> {
            while (runHeavyProcessing) {
            }
          })
        );
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

  public void testDaemonDoesNotDisableItselfDuringVFSRefresh() {
    runWithReparseDelay(0, () -> {
      runHeavyProcessing = false;
      try {
        Set<Editor> applied = Collections.synchronizedSet(new HashSet<>());
        Set<Editor> collected = Collections.synchronizedSet(new HashSet<>());
        registerFakePass(applied, collected);

        configureByText(PlainTextFileType.INSTANCE, "");
        Editor editor = getEditor();
        EditorTracker editorTracker = EditorTracker.getInstance(myProject);
        setActiveEditors(editor);
        while (HeavyProcessLatch.INSTANCE.isRunning()) {
          UIUtil.dispatchAllInvocationEvents();
        }
        type("xxx"); // restart daemon
        assertTrue(editorTracker.getActiveEditors().contains(editor));
        assertSame(editor, FileEditorManager.getInstance(myProject).getSelectedTextEditor());


        // wait for the first pass to complete
        long start = System.currentTimeMillis();
        while (myDaemonCodeAnalyzer.isRunning() || !applied.contains(editor)) {
          UIUtil.dispatchAllInvocationEvents();
          if (System.currentTimeMillis() - start > 1000000) {
            fail("Too long waiting for daemon");
          }
        }

        runHeavyProcessing = true;
        Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() ->
          HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Syncing, "my own vfs refresh", () -> {
            while (runHeavyProcessing);
          })
        );
        while (!HeavyProcessLatch.INSTANCE.isRunning()) {
          UIUtil.dispatchAllInvocationEvents();
        }
        applied.clear();
        collected.clear();

        type("xxx"); // try to restart daemon

        doHighlighting();
        assertNotEmpty(applied);  // it should restart
        assertNotEmpty(collected);
        runHeavyProcessing = false;
        try {
          future.get();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      finally {
        runHeavyProcessing = false;
      }
    });
  }

  public void testDaemonMustDisableItselfDuringDocumentBulkModification() {
    runWithReparseDelay(0, () -> {
      configureByText(PlainTextFileType.INSTANCE, "");
      Editor editor = getEditor();

      Set<Editor> applied = Collections.synchronizedSet(new HashSet<>());
      Set<Editor> collected = Collections.synchronizedSet(new HashSet<>());
      DocumentUtil.executeInBulk(editor.getDocument(), () -> {
        registerFakePass(applied, collected);

        EditorTracker editorTracker = EditorTracker.getInstance(myProject);
        setActiveEditors(editor);
        assertTrue(editorTracker.getActiveEditors().contains(editor));
        assertSame(editor, FileEditorManager.getInstance(myProject).getSelectedTextEditor());

        applied.clear();
        collected.clear();

        myDaemonCodeAnalyzer.restart(); // try to restart daemon

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() < start + 5000) {
          assertEmpty(applied);  // it must not restart
          assertEmpty(collected);
          UIUtil.dispatchAllInvocationEvents();
        }
      });

      applied.clear();
      collected.clear();

      myDaemonCodeAnalyzer.restart(); // try to restart daemon

      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() < start + 5000 && applied.isEmpty()) {
        UIUtil.dispatchAllInvocationEvents();
      }
      assertNotEmpty(applied);  // it must restart outside bulk
      assertNotEmpty(collected);
    });
  }

  public void testModificationInsideCodeBlockDoesNotRehighlightWholeFile() {
    configureByText(JavaFileType.INSTANCE, "class X { int f = \"error\"; int f() { int gg<caret> = 11; return 0;} }");
    HighlightInfo error = assertOneElement(highlightErrors());
    assertEquals("Incompatible types. Found: 'java.lang.String', required: 'int'", error.getDescription());

    error.getHighlighter().dispose();

    assertEmpty(highlightErrors());

    type("23");
    assertEmpty(highlightErrors());

    myEditor.getCaretModel().moveToOffset(0);
    type("/* */");
    HighlightInfo error2 = assertOneElement(highlightErrors());
    assertEquals("Incompatible types. Found: 'java.lang.String', required: 'int'", error2.getDescription());
  }


  public void testModificationInExcludedFileDoesNotCauseRehighlight() {
    @Language("JAVA")
    String text = "class EEE { void f(){} }";
    VirtualFile excluded = configureByText(JavaFileType.INSTANCE, text).getVirtualFile();
    PsiTestUtil.addExcludedRoot(myModule, excluded.getParent());

    configureByText(JavaFileType.INSTANCE, "class X { <caret> }");
    assertEmpty(highlightErrors());
    FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileStatusMap();
    TextRange scope = me.getFileDirtyScope(getEditor().getDocument(), getFile(), Pass.UPDATE_ALL);
    assertNull(scope);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> ((PsiJavaFile)PsiManager.getInstance(myProject).findFile(excluded)).getClasses()[0].getMethods()[0].delete());

    UIUtil.dispatchAllInvocationEvents();
    scope = me.getFileDirtyScope(getEditor().getDocument(), getFile(), Pass.UPDATE_ALL);
    assertNull(scope);
  }

  public void testModificationInWorkspaceXmlDoesNotCauseRehighlight() {
    configureByText(JavaFileType.INSTANCE, "class X { <caret> }");
    StoreUtilKt.runInAllowSaveMode(true, () -> {
      StoreUtil.saveDocumentsAndProjectsAndApp(true);
      VirtualFile workspaceFile = Objects.requireNonNull(getProject().getWorkspaceFile());
      PsiFile excluded = Objects.requireNonNull(PsiManager.getInstance(getProject()).findFile(workspaceFile));

      assertEmpty(highlightErrors());
      FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileStatusMap();
      TextRange scope = me.getFileDirtyScope(getEditor().getDocument(), getFile(), Pass.UPDATE_ALL);
      assertNull(scope);

      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        Document document = Objects.requireNonNull(PsiDocumentManager.getInstance(getProject()).getDocument(excluded));
        document.insertString(0, "<!-- dsfsd -->");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      UIUtil.dispatchAllInvocationEvents();
      scope = me.getFileDirtyScope(getEditor().getDocument(), getFile(), Pass.UPDATE_ALL);
      assertNull(scope);
      return Unit.INSTANCE;
    });
  }

  public void testLightBulbDoesNotUpdateIntentionsInEDT() {
    IntentionAction longLongUpdate = new AbstractIntentionAction() {
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
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        return true;
      }
    };
    IntentionManager.getInstance().addAction(longLongUpdate);
    Disposer.register(getTestRootDisposable(), () -> IntentionManager.getInstance().unregisterIntention(longLongUpdate));
    configureByText(JavaFileType.INSTANCE, "class X { <caret>  }");
    makeEditorWindowVisible(new Point(0, 0), myEditor);
    doHighlighting();
    myDaemonCodeAnalyzer.restart();
    runWithReparseDelay(0, () -> {
      for (int i = 0; i < 1000; i++) {
        caretRight();
        UIUtil.dispatchAllInvocationEvents();
        caretLeft();
        Object updateProgress = new HashMap<>(myDaemonCodeAnalyzer.getUpdateProgress());
        long waitForDaemonStart = System.currentTimeMillis();
        while (myDaemonCodeAnalyzer.getUpdateProgress().equals(updateProgress) && System.currentTimeMillis() < waitForDaemonStart + 5000) { // wait until the daemon started
          UIUtil.dispatchAllInvocationEvents();
        }
        if (myDaemonCodeAnalyzer.getUpdateProgress().equals(updateProgress)) {
          throw new RuntimeException("Daemon failed to start in 5000 ms");
        }
        long start = System.currentTimeMillis();
        while (myDaemonCodeAnalyzer.isRunning() && System.currentTimeMillis() < start + 500) {
          UIUtil.dispatchAllInvocationEvents(); // wait for a bit more until ShowIntentionsPass.doApplyInformationToEditor() called
        }
      }
    });
  }

  static void runWithReparseDelay(int reparseDelayMs, @NotNull Runnable task) {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    int oldDelay = settings.getAutoReparseDelay();
    settings.setAutoReparseDelay(reparseDelayMs);
    try {
      task.run();
    }
    finally {
      settings.setAutoReparseDelay(oldDelay);
    }
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

    assertNotEmpty(highlightErrors());
    UIUtil.dispatchAllInvocationEvents();
    IntentionHintComponent lastHintBeforeDeletion = myDaemonCodeAnalyzer.getLastIntentionHint();
    assertNotNull(lastHintBeforeDeletion);
    assertTrue(lastHintBeforeDeletion.getCachedIntentions().toString(), ContainerUtil.exists(lastHintBeforeDeletion.getCachedIntentions().getErrorFixes(), e -> e.getText().equals("Initialize variable 'var'")));

    delete(myEditor);
    assertNotEmpty(highlightErrors());
    UIUtil.dispatchAllInvocationEvents();
    IntentionHintComponent lastHintAfterDeletion = myDaemonCodeAnalyzer.getLastIntentionHint();
    // it must be either hidden or not have that error anymore
    if (lastHintAfterDeletion == null) {
      assertEmpty(visibleHints);
    }
    else {
      IntentionContainer after = lastHintAfterDeletion.getCachedIntentions();
      assertFalse(after.toString(), ContainerUtil.exists(after.getErrorFixes(), e -> e.getText().equals("Initialize variable 'var'")));
    }
  }

  public void testCodeFoldingPassRestartsOnRegionUnfolding() {
    runWithReparseDelay(0, () -> {
      @Language("JAVA")
      String text = """
        class Foo {
            void m() {

            }
        }""";
      configureByText(JavaFileType.INSTANCE, text);
      EditorTestUtil.buildInitialFoldingsInBackground(myEditor);
      waitForDaemon(myProject, myEditor.getDocument());
      EditorTestUtil.executeAction(myEditor, IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
      waitForDaemon(myProject, myEditor.getDocument());
      checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

      WriteCommandAction.runWriteCommandAction(myProject, () -> myEditor.getDocument().insertString(0, "/*"));
      waitForDaemon(myProject, myEditor.getDocument());
      checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

      EditorTestUtil.executeAction(myEditor, IdeActions.ACTION_EXPAND_ALL_REGIONS);
      waitForDaemon(myProject, myEditor.getDocument());
      checkFoldingState("[FoldRegion -(0:37), placeholder='/.../']");
    });
  }

  public void testChangingSettingsHasImmediateEffectOnOpenedEditor() {
    runWithReparseDelay(0, () -> {
      @Language("JAVA")
      String text = """
        class C {\s
          void m() {
          }\s
        }""";
      configureByText(JavaFileType.INSTANCE, text);
      EditorTestUtil.buildInitialFoldingsInBackground(myEditor);
      waitForDaemon(myProject, myEditor.getDocument());
      checkFoldingState("[FoldRegion -(22:27), placeholder='{}']");

      JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
      boolean savedValue = settings.isCollapseMethods();
      try {
        settings.setCollapseMethods(true);
        CodeFoldingConfigurable.Util.applyCodeFoldingSettingsChanges();
        waitForDaemon(myProject, myEditor.getDocument());
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

  static void waitForDaemon(@NotNull Project project, @NotNull Document document) {
    ThreadingAssertions.assertEventDispatchThread();
    long start = System.currentTimeMillis();
    long deadline = start + 60_000;
    while (!daemonIsWorkingOrPending(project, document)) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      if (System.currentTimeMillis() > deadline) {
        fail("Too long waiting for daemon to start");
      }
    }
    while (daemonIsWorkingOrPending(project, document)) {
      if (System.currentTimeMillis() > deadline) {
        DaemonRespondToChangesPerformanceTest.dumpThreadsToConsole();
        fail("Too long waiting for daemon to finish ("+(System.currentTimeMillis()-start)+"ms already)");
      }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
  }

  static boolean daemonIsWorkingOrPending(@NotNull Project project, @NotNull Document document) {
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    return codeAnalyzer.isRunningOrPending() || PsiDocumentManager.getInstance(project).isUncommited(document);
  }

  public void testRehighlightInDebuggerExpressionFragment() {
    PsiExpressionCodeFragment fragment = JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("+ <caret>\"a\"", null,
                                    PsiType.getJavaLangObject(getPsiManager(), GlobalSearchScope.allScope(getProject())), true);
    myFile = fragment;
    Document document = Objects.requireNonNull(PsiDocumentManager.getInstance(getProject()).getDocument(fragment));
    myEditor = EditorFactory.getInstance().createEditor(document, getProject(), JavaFileType.INSTANCE, false);

    ProperTextRange visibleRange = makeEditorWindowVisible(new Point(0, 0), myEditor);
    assertEquals(document.getTextLength(), visibleRange.getLength());

    try {
      EditorInfo editorInfo = new EditorInfo(document.getText());

      String newFileText = editorInfo.getNewFileText();
      ApplicationManager.getApplication().runWriteAction(() -> {
        if (!document.getText().equals(newFileText)) {
          document.setText(newFileText);
        }

        editorInfo.applyToEditor(myEditor);
      });

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();


      HighlightInfo error = assertOneElement(highlightErrors());
      assertEquals("Operator '+' cannot be applied to 'java.lang.String'", error.getDescription());

      type(" ");

      HighlightInfo after = assertOneElement(highlightErrors());
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
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
      GCWatcher.tracking(documentManager.getCachedPsiFile(document)).ensureCollected();
      assertNull(documentManager.getCachedPsiFile(document));

      @Language("JAVA")
      String text = "class X { void foo() {}}";
      document.insertString(0, text);
      documentManager.commitAllDocuments();
      assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, documentManager.getPsiFile(document), Pass.UPDATE_ALL));

      FileContentUtilCore.reparseFiles(file);
      assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, documentManager.getPsiFile(document), Pass.UPDATE_ALL));

      findClass("X").getMethods()[0].delete();
      assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, documentManager.getPsiFile(document), Pass.UPDATE_ALL));
    });
  }



  public void testDumbAwareHighlightingPassesStartEvenInDumbMode() {
    List<TextEditorHighlightingPassFactory> collected = Collections.synchronizedList(new ArrayList<>());
    List<TextEditorHighlightingPassFactory> applied = Collections.synchronizedList(new ArrayList<>());
      class DumbFac implements TextEditorHighlightingPassFactory, DumbAware {
        @Override
        public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
          return new MyDumbPass(editor, file);
        }

        class MyDumbPass extends EditorBoundHighlightingPass implements DumbAware {
          MyDumbPass(Editor editor, PsiFile file) {
            super(editor, file, false);
          }

          @Override
          public void doCollectInformation(@NotNull ProgressIndicator progress) {
            collected.add(DumbFac.this);
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
      class SmartFac implements TextEditorHighlightingPassFactory {
        @Override
        public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
          return new EditorBoundHighlightingPass(editor, file, false) {
            @Override
            public void doCollectInformation(@NotNull ProgressIndicator progress) {
              collected.add(SmartFac.this);
            }

            @Override
            public void doApplyInformationToEditor() {
              applied.add(SmartFac.this);
            }
          };
        }
      }
    SmartFac smartFac = new SmartFac();
    registrar.registerTextEditorHighlightingPass(smartFac, null, null, false, -1);

    configureByText(PlainTextFileType.INSTANCE, "");
    doHighlighting();
    assertSameElements(collected, dumbFac, smartFac);
    assertSameElements(applied, dumbFac, smartFac);
    collected.clear();
    applied.clear();

    myDaemonCodeAnalyzer.mustWaitForSmartMode(false, getTestRootDisposable());
    DumbModeTestUtils.runInDumbModeSynchronously(myProject, () -> {
      type(' ');
      doHighlighting();

      TextEditorHighlightingPassFactory f = assertOneElement(collected);
      assertSame(dumbFac, f);
      TextEditorHighlightingPassFactory f2 = assertOneElement(applied);
      assertSame(dumbFac, f2);
    });
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
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        DaemonProgressIndicator indicator = (DaemonProgressIndicator)ProgressIndicatorProvider.getGlobalProgressIndicator();
        Throwable alreadyCalled = isAvailableCalled.put(indicator, new Throwable());
        if (alreadyCalled != null) {
          throw new IllegalStateException(" .isAvailable() already called in:\n---------------\n"+ExceptionUtil.getThrowableText(alreadyCalled)+"\n-----------");
        }
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      }
    };
    IntentionManager.getInstance().addAction(action);
    Disposer.register(getTestRootDisposable(), () -> IntentionManager.getInstance().unregisterIntention(action));

    @Language("JAVA")
    String text = "class X { }";
    configureByText(JavaFileType.INSTANCE, text);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().setText(text));
    doHighlighting();
    myDaemonCodeAnalyzer.restart();
    doHighlighting();
  }


  public void testUncommittedByAccidentNonPhysicalDocumentMustNotHangDaemon() {
    ThreadingAssertions.assertEventDispatchThread();
    configureByText(JavaFileType.INSTANCE, "class X { void f() { <caret> } }");
    assertEmpty(highlightErrors());
    assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed());

    PsiFile original = getPsiManager().findFile(getTempDir().createVirtualFile("X.txt", ""));
    assertNotNull(original);
    assertTrue(original.getViewProvider().isEventSystemEnabled());

    PsiFile copy = (PsiFile)original.copy();
    assertFalse(copy.getViewProvider().isEventSystemEnabled());

    Document document = copy.getViewProvider().getDocument();
    assertNotNull(document);
    String text = "class A{}";
    document.setText(text);
    assertFalse(PsiDocumentManager.getInstance(myProject).isCommitted(document));
    assertTrue(PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments());

    type("String i=0;");
    waitForDaemon(myProject, myEditor.getDocument());
    assertNotEmpty(DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.ERROR, getProject()));
    assertEquals(text, document.getText());  // retain non-phys document until after highlighting
    assertFalse(PsiDocumentManager.getInstance(myProject).isCommitted(document));
    assertTrue(PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments());
  }

  public void testPutArgumentsOnSeparateLinesIntentionMustNotRemoveErrorHighlighting() {
    configureByText(JavaFileType.INSTANCE, "class X{ static void foo(String s1, String s2, String s3) { foo(\"1\", 1.2, \"2\"<caret>); }}");
    assertOneElement(highlightErrors());

    List<IntentionAction> fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
    IntentionAction intention = assertContainsOneOf(fixes, "Put arguments on separate lines");
    assertNotNull(intention);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> intention.invoke(getProject(), getEditor(), getFile()));
    assertOneElement(highlightErrors());
  }


  public void testHighlightingPassesAreInstantiatedOutsideEDTToImproveResponsiveness() throws Throwable {
    AtomicReference<Throwable> violation = new AtomicReference<>();
    AtomicBoolean applied = new AtomicBoolean();
    class MyCheckingConstructorTraceFac implements TextEditorHighlightingPassFactory {
      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
        return new MyPass(myProject);
      }

      final class MyPass extends TextEditorHighlightingPass {
        private MyPass(Project project) {
          super(project, getEditor().getDocument(), false);
          if (ApplicationManager.getApplication().isDispatchThread()) {
            violation.set(new Throwable());
          }
        }

        @Override
        public void doCollectInformation(@NotNull ProgressIndicator progress) {
        }

        @Override
        public void doApplyInformationToEditor() {
          applied.set(true);
        }
      }
    }
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(getProject());
    registrar.registerTextEditorHighlightingPass(new MyCheckingConstructorTraceFac(), null, null, false, -1);
    configureByText(JavaFileType.INSTANCE, "class C{}");
    assertEmpty(highlightErrors());
    assertTrue(applied.get());
    if (violation.get() != null) {
      throw violation.get();
    }
  }

  private static class EmptyPassFactory implements TextEditorHighlightingPassFactory {
    @Override
    public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
      return new EmptyPass(file.getProject(), editor.getDocument());
    }

    static class EmptyPass extends TextEditorHighlightingPass {
      private EmptyPass(Project project, @NotNull Document document) {
        super(project, document, false);
      }

      @Override
      public void doCollectInformation(@NotNull ProgressIndicator progress) {
      }

      @Override
      public void doApplyInformationToEditor() {
      }
    }
  }

  public void testTextEditorHighlightingPassRegistrarMustNotAllowCyclesInPassDeclarationsOrCrazyPassIdsOmgMurphyLawStrikesAgain() {
    configureByText(JavaFileType.INSTANCE, "class C{}");
    TextEditorHighlightingPassRegistrarImpl registrar = (TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(getProject());
    int F2 = Pass.EXTERNAL_TOOLS;
    int forcedId1 = 256;
    int forcedId2 = 257;
    int forcedId3 = 258;
    assertThrows(IllegalArgumentException.class, () ->
      // afterCompletionOf and afterStartingOf must not intersect
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{F2}, new int[]{F2}, false, -1));
    assertThrows(IllegalArgumentException.class, () ->
      // afterCompletionOf and afterStartingOf must not contain forcedId
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId1));
    assertThrows(IllegalArgumentException.class, () ->
      // afterCompletionOf and afterStartingOf must not contain forcedId
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), null, new int[]{forcedId1}, false, forcedId1));
    assertThrows(IllegalArgumentException.class, () ->
      // afterCompletionOf and afterStartingOf must not contain crazy ids
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{0}, new int[]{F2}, false, forcedId1));
    assertThrows(IllegalArgumentException.class, () ->
      // afterCompletionOf and afterStartingOf must not contain crazy ids
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{-1}, new int[]{F2}, false, forcedId1));
    assertThrows(IllegalArgumentException.class, () ->
      // afterCompletionOf and afterStartingOf must not contain crazy ids
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{F2}, new int[]{0}, false, forcedId1));
    assertThrows(IllegalArgumentException.class, () ->
      // afterCompletionOf and afterStartingOf must not contain crazy ids
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{F2}, new int[]{-1}, false, forcedId1));
    assertThrows(IllegalArgumentException.class, () ->
      // afterCompletionOf and afterStartingOf must not contain crazy ids
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{32134}, new int[]{F2}, false, forcedId1));
    assertThrows(IllegalArgumentException.class, () ->
      // afterCompletionOf and afterStartingOf must not contain crazy ids
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{F2}, new int[]{982314}, false, forcedId1));

    assertThrows(IllegalArgumentException.class, () -> {
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId2}, null, false, forcedId1);
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId2);
      assertEmpty(highlightErrors());
    });
    // non-direct cycle
    assertThrows(IllegalArgumentException.class, () -> {
      registrar.reRegisterFactories(); // clear caches from incorrect factories above
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId2}, null, false, forcedId1);
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId3}, null, false, forcedId2);
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId3);
      assertEmpty(highlightErrors());
    });

    registrar.reRegisterFactories(); // clear caches from incorrect factories above
    registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), null, null, false, forcedId1);
    registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId3);
    registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId3}, null, false, forcedId2);
    assertEmpty(highlightErrors());
  }

  public void testHighlightInfoMustImmediatelyShowItselfOnScreenRightAfterCreation() {
    AtomicBoolean xxxMustBeVisible = new AtomicBoolean();
    HighlightVisitor visitor = new MyHighlightCommentsSubstringVisitor(xxxMustBeVisible);
    myProject.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, getTestRootDisposable());
    @Language("JAVA")
    String text = """
      class X {
        void f(boolean b) {
          if (b) {
            // xxx
          }
        }
      }
      """;
    configureByText(JavaFileType.INSTANCE, text);

    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
    Runnable callbackWhileWaiting = () -> {
      if (xxxMustBeVisible.get()) {
        List<String> myInfos = ContainerUtil.map(filterMy(DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject())), h->h.getDescription());
        assertTrue(myInfos.toString(), myInfos.contains("MY: XXX"));
      }
    };
    myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), textEditor, ArrayUtilRt.EMPTY_INT_ARRAY, false, callbackWhileWaiting);

    List<HighlightInfo> myWarns = filterMy(doHighlighting());
    assertEquals(myWarns.toString(), 2, myWarns.size());
  }

  @NotNull
  private static List<HighlightInfo> filterMy(@NotNull List<? extends HighlightInfo> infos) {
    return ContainerUtil.filter(infos, h -> h.getDescription() != null && h.getDescription().startsWith("MY: "));
  }

  private static class MyHighlightCommentsSubstringVisitor implements HighlightVisitor {
    private final AtomicBoolean xxxMustBeVisible;
    private HighlightInfoHolder myHolder;

    MyHighlightCommentsSubstringVisitor(@NotNull AtomicBoolean xxxMustBeVisible) {
      this.xxxMustBeVisible = xxxMustBeVisible;
    }

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
      return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
      if (element instanceof PsiComment) {
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element.getTextRange().cutOut(TextRange.create(0, 1))).description("MY: XXX").create());
        // immediately after creation
        ApplicationManager.getApplication().invokeLater(()->xxxMustBeVisible.set(true));
        TimeoutUtil.sleep(10_000);
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element.getTextRange().cutOut(TextRange.create(1, 2))).description("MY: XXX2").create());
      }
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
      return new MyHighlightCommentsSubstringVisitor(xxxMustBeVisible);
    }
  }

  public void testHighlightInfoMustImmediatelyShowItselfOnScreenRightAfterCreationInBGT() {
    Runnable commentHighlighted = () -> {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      ApplicationManager.getApplication().assertReadAccessAllowed();

      // assert markup is updated as soon as the HighlightInfo is created
      List<HighlightInfo> highlightsFromMarkup = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject());
      MyHighlightCommentVisitor.assertHighlighted(highlightsFromMarkup);
    };
    HighlightVisitor visitor = new MyHighlightCommentVisitor(commentHighlighted);
    myProject.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, getTestRootDisposable());
    @Language("JAVA")
    String text = """
      class X {
        void f(boolean b) {
          if (b) {
            // xxx
          }
        }
      }
      """;
    configureByText(JavaFileType.INSTANCE, text);

    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    MyHighlightCommentVisitor.assertHighlighted(infos);
  }

  private static class MyHighlightCommentVisitor implements HighlightVisitor {
    private final Runnable commentHighlighted;
    private HighlightInfoHolder myHolder;

    private MyHighlightCommentVisitor(@NotNull Runnable commentHighlighted) {
      this.commentHighlighted = commentHighlighted;
    }

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
      return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
      if (element instanceof PsiComment) {
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element.getTextRange()).description("MY2: CMT").create());
        commentHighlighted.run();
      }
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
      return new MyHighlightCommentVisitor(commentHighlighted);
    }

    private static void assertHighlighted(List<? extends HighlightInfo> infos) {
      assertTrue("HighlightInfo is missing. All available infos are: "+infos, ContainerUtil.exists(infos, info -> info.getDescription().equals("MY2: CMT")));
    }
  }

  public void testDaemonListenerFiresEventsInCorrectOrder() {
    List<String> log = Collections.synchronizedList(new ArrayList<>());
    myProject.getMessageBus().connect(getTestRootDisposable())
      .subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
          log.add("F");
        }

        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          log.add("S");
        }

        @Override
        public void daemonCancelEventOccurred(@NotNull String reason) {
          log.add("C");
        }
      });

    @Language("JAVA")
    String text = """
      class X {
        // comment1
        // comment2
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    assertEmpty(highlightErrors());

    log.clear();
    INTERRUPT.set(true);
    HighlightVisitor visitor = new MyInterruptingVisitor();
    myProject.getExtensionArea().getExtensionPoint(HighlightVisitor.EP_HIGHLIGHT_VISITOR).registerExtension(visitor, getTestRootDisposable());

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());

    myDaemonCodeAnalyzer.restart();
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    try {
      myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), TextEditorProvider.getInstance().getTextEditor(getEditor()), ArrayUtilRt.EMPTY_INT_ARRAY, true, () -> {});
    }
    catch (ProcessCanceledException ignored) {
    }

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject());
    MyInterruptingVisitor.assertHighlighted(infos);
    assertEquals("[S, C]", log.toString());

    INTERRUPT.set(false);
    log.clear();
    try {
      myDaemonCodeAnalyzer.runPasses(getFile(), getEditor().getDocument(), TextEditorProvider.getInstance().getTextEditor(getEditor()), ArrayUtilRt.EMPTY_INT_ARRAY, true, () -> { });
    }
    catch (ProcessCanceledException ignored) {
    }
    finally {
      myDaemonCodeAnalyzer.setUpdateByTimerEnabled(true);
    }
    infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.WARNING, getProject());
    MyInterruptingVisitor.assertHighlighted(infos);
    assertEquals("[S, F]", log.toString());
  }

  private static final AtomicBoolean INTERRUPT = new AtomicBoolean();
  private static class MyInterruptingVisitor implements HighlightVisitor {
    private HighlightInfoHolder myHolder;

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
      return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
      if (element instanceof PsiComment) {
        myHolder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element.getTextRange()).description("MY3: CMT").create());
        if (INTERRUPT.get()) {
          throw new ProcessCanceledException();
        }
      }
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
      return new MyInterruptingVisitor();
    }

    private static void assertHighlighted(List<? extends HighlightInfo> infos) {
      assertTrue("HighlightInfo is missing. All available infos are: "+infos, ContainerUtil.exists(infos, info -> info.getDescription().equals("MY3: CMT")));
    }
  }

  public void testHighlightersMustDisappearWhenTheHighlightingIsSwitchedOff() {
    @Language("JAVA")
    String text = """
      class X {
        blah blah
        )(@*$)(*%@$)
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    assertNotEmpty(highlightErrors());
    HighlightingSettingsPerFile.getInstance(getProject()).setHighlightingSettingForRoot(getFile(), FileHighlightingSetting.SKIP_HIGHLIGHTING);

    assertEmpty(highlightErrors());
  }
}
