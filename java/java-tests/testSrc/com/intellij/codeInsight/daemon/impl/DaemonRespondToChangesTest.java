// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.application.options.editor.CodeFoldingConfigurable;
import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.EditorInfo;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspectionBase;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.configurationStore.StoreUtilKt;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.lang.LanguageFilter;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
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
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
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
import com.intellij.util.*;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import kotlin.Unit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * tests general daemon behaviour/interruptibility/restart during highlighting
 */
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
    (EditorTracker.Companion.getInstance(myProject)).setActiveEditors(Arrays.asList(editors));
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
    @Language("JAVA")
    String text = """
      package x;\s
      class ClassA {
          static <T> void sayHello(Class<? extends T> msg) {}
      }
      class ClassB extends ClassA {
          static <T extends String> void sayHello(Class<? extends T> msg) {<caret>
          }
      }
      """;
    configureByText(JavaFileType.INSTANCE, text);

    assertOneElement(highlightErrors());
    type("//my comment inside method body, so class modifier won't be visited");
    assertOneElement(highlightErrors());
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

  public void testFileStatusMapDirtyPSICachingWorks() {
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
        return new TestFileStatusMapDirtyCachingWorksPass(myProject);
      }

      final class TestFileStatusMapDirtyCachingWorksPass extends TextEditorHighlightingPass {
        private TestFileStatusMapDirtyCachingWorksPass(Project project) {
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

  public void testFileStatusMapDirtyDocumentRangeWorks() {
    configureByText(PlainTextFileType.INSTANCE, "class <caret>S { int ffffff =  0;}");
    UIUtil.dispatchAllInvocationEvents();

    Document document = myEditor.getDocument();
    FileStatusMap fileStatusMap = myDaemonCodeAnalyzer.getFileStatusMap();
    fileStatusMap.disposeDirtyDocumentRangeStorage(document);
    assertEquals(TextRange.EMPTY_RANGE, fileStatusMap.getCompositeDocumentDirtyRange(document));

    int offset = myEditor.getCaretModel().getOffset();
    type(' ');
    assertEquals(new TextRange(offset, offset+1), fileStatusMap.getCompositeDocumentDirtyRange(document));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(10, 11, "xxx"));
    assertEquals(new TextRange(offset, 13), fileStatusMap.getCompositeDocumentDirtyRange(document));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("  "));
    assertEquals(new TextRange(0, 2), fileStatusMap.getCompositeDocumentDirtyRange(document));
    fileStatusMap.disposeDirtyDocumentRangeStorage(document);
    assertEquals(new TextRange(0, 0), fileStatusMap.getCompositeDocumentDirtyRange(document));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0,"x"));
    assertEquals(new TextRange(0, 1), fileStatusMap.getCompositeDocumentDirtyRange(document));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(1,"x"));
    assertEquals(new TextRange(0, 2), fileStatusMap.getCompositeDocumentDirtyRange(document));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(4,"x"));
    assertEquals(new TextRange(0, 5), fileStatusMap.getCompositeDocumentDirtyRange(document));
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

    enableDeadCodeInspection();

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

  private void enableDeadCodeInspection() {
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    HighlightDisplayKey myDeadCodeKey = HighlightDisplayKey.findOrRegister(UnusedDeclarationInspectionBase.SHORT_NAME,
                                                                           UnusedDeclarationInspectionBase.getDisplayNameText(), UnusedDeclarationInspectionBase.SHORT_NAME);
    UnusedDeclarationInspectionBase myDeadCodeInspection = new UnusedDeclarationInspectionBase(true);
    enableInspectionTool(myDeadCodeInspection);
    assert profile.isToolEnabled(myDeadCodeKey, myFile);
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
      Collection<Editor> applied = ContainerUtil.createConcurrentList();
      Collection<Editor> collected = ContainerUtil.createConcurrentList();
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
        EditorTracker editorTracker = EditorTracker.Companion.getInstance(myProject);
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
        EditorTracker editorTracker = EditorTracker.Companion.getInstance(myProject);
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
            while (runHeavyProcessing) {
              Thread.yield();
            }
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

        EditorTracker editorTracker = EditorTracker.Companion.getInstance(myProject);
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
          return new TestDumbAwareHighlightingPassesStartEvenInDumbModePass(editor, file);
        }

        class TestDumbAwareHighlightingPassesStartEvenInDumbModePass extends EditorBoundHighlightingPass implements DumbAware {
          TestDumbAwareHighlightingPassesStartEvenInDumbModePass(Editor editor, PsiFile file) {
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
        return new TestHighlightingPassesAreInstantiatedOutsideEDTToImproveResponsivenessPass(myProject);
      }

      final class TestHighlightingPassesAreInstantiatedOutsideEDTToImproveResponsivenessPass extends TextEditorHighlightingPass {
        private TestHighlightingPassesAreInstantiatedOutsideEDTToImproveResponsivenessPass(Project project) {
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

  public void testTypingErrorElementMustHighlightIt() {
    ThreadingAssertions.assertEventDispatchThread();
    configureByText(JavaFileType.INSTANCE, "class X { void f() { } }<caret>");
    assertEmpty(highlightErrors());
    makeEditorWindowVisible(new Point(0, 1000), myEditor);

    type("/");
    waitForDaemon(myProject, myEditor.getDocument());
    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.ERROR, getProject());
    assertNotEmpty(errors);
    assertTrue(errors.toString().contains("'class' or 'interface' expected"));
  }

  public void testTypingInsideCodeBlockCanAffectUnusedDeclarationInTheOtherClass() {
    enableInspectionTool(new UnusedSymbolLocalInspection());
    enableDeadCodeInspection();
    configureByFiles(null, BASE_PATH+getTestName(true)+"/p2/A2222.java", BASE_PATH+getTestName(true)+"/p1/A1111.java");
    assertEquals("A2222.java", getFile().getName());

    HighlightInfo info = assertOneElement(doHighlighting(HighlightSeverity.WARNING));
    assertEquals("Class 'A2222' is never used", info.getDescription());

    Document document1111 = getFile().getParent().findFile("A1111.java").getFileDocument();
    // uncomment (inside code block) the reference to A2222
    WriteCommandAction.writeCommandAction(myProject).run(()->document1111.deleteString(document1111.getText().indexOf("//"), document1111.getText().indexOf("//")+2));

    // now A2222 is no longer unused
    assertEmpty(doHighlighting(HighlightSeverity.WARNING));
  }

  // test the other type of PSI change: child remove/child add
  public void testTypingInsideCodeBlockCanAffectUnusedDeclarationInTheOtherClass2() {
    enableInspectionTool(new UnusedSymbolLocalInspection());
    enableDeadCodeInspection();
    configureByFiles(null, BASE_PATH+getTestName(true)+"/p1/A1111.java", BASE_PATH+getTestName(true)+"/p2/A2222.java");
    assertEquals("A1111.java", getFile().getName());
    makeEditorWindowVisible(new Point(0, 1000), myEditor);
    HighlightInfo info = assertOneElement(doHighlighting(HighlightSeverity.WARNING));
    assertEquals("Method 'foo()' is never used", info.getDescription());

    Document document2222 = getFile().getParent().findFile("A2222.java").getFileDocument();
    // uncomment (inside code block) the reference to A1111
    WriteCommandAction.writeCommandAction(myProject).run(()->document2222.deleteString(document2222.getText().indexOf("//"), document2222.getText().indexOf("//")+2));

    // now foo() is no longer unused
    assertEmpty(doHighlighting(HighlightSeverity.WARNING));
  }

  public void testTypingDoesNotLeaveInvalidPSIShitBehind() {
    String text = """
      class X {
        void f() {
          ///
          int s;<caret>
          ///
        }
      }""";
    String bigText = text.replaceAll("///\n", "hashCode();\n".repeat(1000));
    configureByText(JavaFileType.INSTANCE, bigText);
    makeEditorWindowVisible(new Point(0, 1000), myEditor);
    assertEmpty(doHighlighting(HighlightSeverity.ERROR));
    MarkupModel markupModel = DocumentMarkupModel.forDocument(myEditor.getDocument(), getProject(), true);
    for (int i=0; i<10; i++) {
      type(" // TS");
      assertEmpty(doHighlighting(HighlightSeverity.ERROR));
      assertEmpty(getErrorsFromMarkup(markupModel));
      
      backspace();backspace();backspace();backspace();backspace();backspace();
      assertEmpty(doHighlighting(HighlightSeverity.ERROR));
      assertEmpty(getErrorsFromMarkup(markupModel));
    }
  }

  // highlights all //XXX, but very slow
  public static class MyVerySlowAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    private static final AtomicBoolean wait = new AtomicBoolean();
    private static final String SWEARING = "No swearing";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        while (wait.get()) {
          Thread.yield();
        }
        holder.newAnnotation(HighlightSeverity.ERROR, SWEARING).range(element).create();
        iDidIt();
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
    }
    static List<HighlightInfo> myHighlights(MarkupModel markupModel) {
      return Arrays.stream(markupModel.getAllHighlighters())
        .map(highlighter -> HighlightInfo.fromRangeHighlighter(highlighter))
        .filter(Objects::nonNull)
        .filter(info -> SWEARING.equals(info.getDescription())).toList();
    }
    static List<HighlightInfo> syntaxHighlights(MarkupModel markupModel, String description) {
      List<HighlightInfo> errors = Arrays.stream(markupModel.getAllHighlighters())
            .map(highlighter -> HighlightInfo.fromRangeHighlighter(highlighter))
            .filter(Objects::nonNull)
            .filter(info -> description.equals(info.getDescription())).toList();
      return errors;
    }
  }

  public void testInvalidPSIElementsCreatedByTypingNearThemMustBeRemovedImmediatelyMeaningLongBeforeTheHighlightingPassFinished() {
    @Language("JAVA")
    String text = """
      class X {
        void f() {
          //XXX
      
          int s-<caret>; // ';' expected
      
        }
      }""";

    assertInvalidPSIElementHighlightingIsRemovedImmediatelyAfterRepairingChange(text, "';' expected", () -> backspace());
  }

  public void testInvalidPSIElementsCreatedByTypingNearThemMustBeRemovedImmediatelyMeaningLongBeforeTheHighlightingPassFinished2() {
    @Language("JAVA")
    String text = """
      class X {
        void f() {
          //XXX
      
          <caret> # // Unexpected token
      
        }
      }""";

    assertInvalidPSIElementHighlightingIsRemovedImmediatelyAfterRepairingChange(text, "Unexpected token", () -> type("//"));
  }

  private void assertInvalidPSIElementHighlightingIsRemovedImmediatelyAfterRepairingChange(@Language("JAVA") String text,
                                                                                           String errorDescription,
                                                                                           Runnable repairingChange // the change which is supposed to fix the invalid PSI highlight
  ) {
    configureByText(JavaFileType.INSTANCE, text);
    makeEditorWindowVisible(new Point(0, 1000), myEditor);
    MyVerySlowAnnotator.wait.set(false);
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyVerySlowAnnotator()}, ()->{
      MarkupModel markupModel = DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);
      doHighlighting();
      assertNotEmpty(MyVerySlowAnnotator.syntaxHighlights(markupModel, errorDescription));
      assertNotEmpty(MyVerySlowAnnotator.myHighlights(markupModel));

      MyVerySlowAnnotator.wait.set(true);
      repairingChange.run(); //repair invalid psi
      AtomicBoolean success = new AtomicBoolean();
      // register very slow annotator and make sure the invalid PSI highlighting was removed before this annotator finished
      TestTimeOut n = TestTimeOut.setTimeout(100, TimeUnit.SECONDS);
      Runnable checkHighlighted = () -> {
        UIUtil.dispatchAllInvocationEvents();
        if (MyVerySlowAnnotator.syntaxHighlights(markupModel, errorDescription).isEmpty() && MyVerySlowAnnotator.wait.get()) {
          // removed before highlighting is finished
          MyVerySlowAnnotator.wait.set(false);
          success.set(true);
        }
        if (n.timedOut()) {
          MyVerySlowAnnotator.wait.set(false);
          throw new RuntimeException(new TimeoutException(ThreadDumper.dumpThreadsToString()));
        }
      };
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
      myDaemonCodeAnalyzer.runPasses(myFile, myEditor.getDocument(), textEditor, new int[0], false, checkHighlighted);

      assertEmpty(MyVerySlowAnnotator.syntaxHighlights(markupModel, errorDescription));
      assertNotEmpty(MyVerySlowAnnotator.myHighlights(markupModel));
      assertTrue(success.get());
    });
  }

  @Language(value = "JAVA", prefix="class X { void foo() {\n", suffix = "\n}\n}")
  String MANY_LAMBDAS_TEXT_TO_TYPE = """
    if (i(()->{
            i(()-> {
              System.out.println("vFile = ");
            });
          })) {
       i(new Runnable() {
           @Override public void run() {
              if (true==true) { return; 
              } 
           }
        });
      }
    else {
      // return this
    }
    """;

  @Language("JAVA")
  String MANY_LAMBDAS_INITIAL = """
    class X {
      void invokeLater(Runnable r) {}
      boolean i(Runnable r) { return true;}
      void foo() {
       <caret>
      }
    }""";
  public void testDaemonDoesRestartDuringMadMonkeyTyping/*Stress*/() {
    assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(MANY_LAMBDAS_INITIAL, MANY_LAMBDAS_TEXT_TO_TYPE, null);
  }
  @Language(value = "JAVA", prefix="class X { void foo() {\n", suffix = "\n}\n}")
  String LONG_LINE_WITH_PARENS_TEXT_TO_TYPE = """
    if (highlighter != null) highlighter += " text='" + StringUtil.first(getText(), 40, true) + "'";
    """;
  @Language("JAVA")
  String LONG_LINE_WITH_PARENS_INITIAL_TEXT = """
    class X {
      static String getText() { return ""; }
      static class StringUtil {
        static String first(String t, int length, boolean b) { return t; }
      }
      String highlighter;
      void foo() {
       <caret>
      }
    }""";
  public void testDaemonDoesRestartDuringMadMonkeyTyping2/*Stress*/() {
    assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(LONG_LINE_WITH_PARENS_INITIAL_TEXT, LONG_LINE_WITH_PARENS_TEXT_TO_TYPE, null);
  }

  public void testDaemonDoesNotLeaveObsoleteErrorElementHighlightsBehind/*Stress*/() {
    Random random = new Random();
    assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(MANY_LAMBDAS_INITIAL, MANY_LAMBDAS_TEXT_TO_TYPE, () -> TimeoutUtil.sleep(random.nextInt(10)));
  }
  public void testDaemonDoesNotLeaveObsoleteErrorElementHighlightsBehind2/*Stress*/() {
    Random random = new Random();
    assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(LONG_LINE_WITH_PARENS_INITIAL_TEXT, LONG_LINE_WITH_PARENS_TEXT_TO_TYPE, () -> TimeoutUtil.sleep(random.nextInt(10)));
  }

  // start typing in the empty java file char by char
  // after each typing, wait for the daemon to start and immediately proceed to type the next char
  // thus making daemon interrupt itself constantly, in hope for multiple highlighting sessions overlappings to manifest themselves more quickly.
  // after all typings are over, wait for final highlighting to complete and check that no errors are left in the markup
  private void assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(String initialText, String textToType, Runnable afterWaitForDaemon) {
    // run expensive consistency checks on each typing
    HighlightInfoUpdaterImpl.ASSERT_INVARIANTS = true; Disposer.register(getTestRootDisposable(), () -> HighlightInfoUpdaterImpl.ASSERT_INVARIANTS = false);
    String finalText = initialText.replace("<caret>", textToType);
    configureByText(JavaFileType.INSTANCE, finalText);
    HighlightInfoUpdaterImpl updater = (HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(getProject());
    assertEmpty(doHighlighting(HighlightSeverity.ERROR));
    runWithReparseDelay(0, () -> {
      for (int i=0; i<10; i++) {
        //System.out.println("i = " + i);
        PassExecutorService.LOG.debug("i = " + i);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().setText("  "));
        doHighlighting(); // reset various optimizations e.g. FileStatusMap.getCompositeDocumentDirtyRange
        MarkupModel markupModel = DocumentMarkupModel.forDocument(myEditor.getDocument(), getProject(), true);
        for (int c = 0; c < finalText.length(); c++) {
          PassExecutorService.LOG.debug("c = " + c);
          //System.out.println("  c = " + c);
          int o=c;
          //updater.assertNoDuplicates(myFile, getErrorsFromMarkup(markupModel), "errors from markup ");
          WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            assertFalse(myDaemonCodeAnalyzer.isRunning());
            type(finalText.charAt(o));
            assertFalse(myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile));
          });
          //updater.assertNoDuplicates(myFile, getErrorsFromMarkup(markupModel), "errors from markup ");
          TestTimeOut t = TestTimeOut.setTimeout(30, TimeUnit.SECONDS);
          myDaemonCodeAnalyzer.restart(myFile);
          List<HighlightInfo> errorsFromMarkup = getErrorsFromMarkup(markupModel);
          //updater.assertNoDuplicates(myFile, errorsFromMarkup, "errors from markup ");
          //((HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(getProject())).assertMarkupDataConsistent(myFile);
          PassExecutorService.LOG.debug(" errorsfrommarkup:\n" + StringUtil.join(ContainerUtil.sorted(errorsFromMarkup, Segment.BY_START_OFFSET_THEN_END_OFFSET), "\n") + "\n-----\n");
          while (!myDaemonCodeAnalyzer.isRunning() && !myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile)/*in case the highlighting has already finished miraculously by now*/) {
            Thread.yield();
            UIUtil.dispatchAllInvocationEvents();
            if (t.timedOut()) throw new RuntimeException(new TimeoutException());
          }
          if (afterWaitForDaemon != null) {
            afterWaitForDaemon.run();
          }
        }
        // some chars might be inserted by TypeHandlers
        while (!myEditor.getDocument().getText().substring(myEditor.getCaretModel().getOffset()).isEmpty()) {
          delete(myEditor);
        }
        LOG.debug("All typing completed. " +
                  "\neditor text:-----------\n"+myEditor.getDocument().getText()+"\n-------\n"+
                  "errors in markup: " + StringUtil.join(getErrorsFromMarkup(markupModel), "\n") + "\n-----\n");
        waitForDaemon(getProject(), myEditor.getDocument());
        assertEmpty(myEditor.getDocument().getText(), getErrorsFromMarkup(markupModel));
      }
    });
  }

  private static @NotNull List<HighlightInfo> getErrorsFromMarkup(MarkupModel model) {
    return Arrays.stream(model.getAllHighlighters())
      .map(m -> HighlightInfo.fromRangeHighlighter(m))
      .filter(Objects::nonNull)
      .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
      .toList();
  }
}
