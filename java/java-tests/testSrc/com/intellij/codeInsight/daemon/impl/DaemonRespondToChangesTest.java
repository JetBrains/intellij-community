// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.application.options.editor.CodeFoldingConfigurable;
import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.EditorInfo;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerTestCase;
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
import com.intellij.javaee.ExternalResourceManagerExBase;
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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.inline.InlineRefactoringActionHandler;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.DocumentUtil;
import com.intellij.util.TestTimeOut;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ref.GCWatcher;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import kotlin.Unit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * tests general daemon behaviour/interruptibility/restart during highlighting
 */
@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class DaemonRespondToChangesTest extends ProductionDaemonAnalyzerTestCase {
  public static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typing/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
    UndoManager.getInstance(myProject);
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

  private void setActiveEditors(Editor @NotNull ... editors) {
    EditorTracker.getInstance(myProject).setActiveEditorsInTests(List.of(editors));
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

  public void testTypingSpaceInsideError() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass {
        {
          toString(0,<caret>0);
        }
      }
    """);
    assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    for (int i = 0; i < 100; i++) {
      type(" ");
      assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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
    assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    backspace();
    assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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
    List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    assertSize(1, infos);
    assertEquals("Private field 'ffff' is never used", infos.getFirst().getDescription());

    type("  foo(ffff++);");
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, getProject());
    assertSize(0, errors);
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
    List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    assertEmpty(infos);

    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE);
    infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);

    assertSize(1, infos);
    assertEquals("Method 'ffff()' is never used", infos.getFirst().getDescription());
  }


  public void testAssignedButUnreadFieldUpdate() throws Exception {
    configureByFile(BASE_PATH + "AssignedButUnreadField.java");
    List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    assertSize(1, infos);
    assertEquals("Private field 'text' is assigned but never accessed", infos.getFirst().getDescription());

    ctrlW();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> EditorModificationUtilEx.deleteSelectedText(getEditor()));
    type("  text");

    List<HighlightInfo> errors = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    assertEmpty(getFile().getText(), errors);
  }

  public void testDaemonIgnoresNonPhysicalEditor() throws Exception {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
    
      }
    """);
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    EditorFactory editorFactory = EditorFactory.getInstance();
    Document consoleDoc = editorFactory.createDocument("my console blah");
    Editor consoleEditor = editorFactory.createEditor(consoleDoc);

    try {
      checkDaemonReaction(false, () -> caretRight(consoleEditor));
      checkDaemonReaction(true, () -> {
        boolean f1 = myDaemonCodeAnalyzer.isAllAnalysisFinished();
        typeInAlienEditor(consoleEditor, 'y');
        boolean f2 = myDaemonCodeAnalyzer.isAllAnalysisFinished();
        LOG.debug("f1 = " + f1+"; f2 = " + f2+"; "+myDaemonCodeAnalyzer.getUpdateProgress());
      });
      checkDaemonReaction(true, () -> LightPlatformCodeInsightTestCase.backspace(consoleEditor, getProject()));

      //real editor
      checkDaemonReaction(true, ()-> caretRight());
    }
    finally {
      editorFactory.releaseEditor(consoleEditor);
    }
  }


  public void testDaemonIgnoresConsoleActivities() throws Exception {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
    
      }
    """);

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(getProject()).getConsole();

    consoleView.getComponent(); //create editor
    consoleView.print("haha", ConsoleViewContentType.NORMAL_OUTPUT);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    try {
      checkDaemonReaction(false, () -> {
        consoleView.clear();
        try {
          Thread.sleep(300); // *&^ing alarm
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); //flush
      });
      checkDaemonReaction(false, () -> {
        consoleView.print("sss", ConsoleViewContentType.NORMAL_OUTPUT);
        try {
          Thread.sleep(300); // *&^ing alarm
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); //flush
      });
      checkDaemonReaction(false, () -> {
        consoleView.setOutputPaused(true);
        try {
          Thread.sleep(300); // *&^ing alarm
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); //flush
      });
    }
    finally {
      Disposer.dispose(consoleView);
    }
  }

  @RequiresEdt
  private void checkDaemonReaction(boolean mustCancelItself, @NotNull Runnable action) throws Exception {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    myTestDaemonCodeAnalyzer.waitForTermination();

    AtomicBoolean ran = new AtomicBoolean();
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

          @Override
          public void daemonStarting(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
            if (!ran.getAndSet(true)) {
              action.run();
            }
          }
        });
    try {
      myDaemonCodeAnalyzer.restart(getTestName(false));
      try {
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), getEditor().getDocument(), ()->{});
      }
      catch (ProcessCanceledException ignored) {
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

    try {
      String location = getTestName(false) + ".xsd";
      final String url = "http://myschema/";
      ExternalResourceManagerExBase.registerResourceTemporarily(url, location, getTestRootDisposable());

      configureByFiles(null, BASE_PATH + getTestName(false) + ".xml", BASE_PATH + getTestName(false) + ".xsd");

      assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

      Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
      Editor schemaEditor = null;
      for (Editor editor : allEditors) {
        Document document = editor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
        if (psiFile == null) continue;
        if (location.equals(psiFile.getName())) {
          schemaEditor = editor;
          break;
        }
      }
      delete(Objects.requireNonNull(schemaEditor));

      List<HighlightInfo> errors =
        myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
      assertNotEmpty(errors);
    }
    finally {
      for (LanguageFilter extension : extensions) {
        XMLLanguage.INSTANCE.registerLanguageExtension(extension);
      }
    }
  }


  public void testRehighlightInnerBlockAfterInline() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    HighlightInfo error = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    assertEquals("Variable 'e' is already defined in the scope", error.getDescription());
    PsiElement element = getFile().findElementAt(getEditor().getCaretModel().getOffset()).getParent();

    DataContext dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT, element, ((EditorEx)getEditor()).getDataContext());
    new InlineRefactoringActionHandler().invoke(getProject(), getEditor(), getFile(), dataContext);

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }


  public void testRangeMarkersDoNotGetAddedOrRemovedWhenUserIsJustTypingInsideHighlightedRegionAndEspeciallyInsideInjectedFragmentsWhichAreColoredGreenAndUsersComplainEndlesslyThatEditorFlickersThere() {
    configureByText(JavaFileType.INSTANCE, """
      class S { int f() {
          return <caret>hashCode();
      }}""");

    Collection<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertSize(3, infos);

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
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

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

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(getFile()), getProject(), true);
    modelEx.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        if (highlighter.getTextRange().substring(highlighter.getDocument().getText()).equals("TTTTTTTTTTTTTTT")) {
          throw new RuntimeException("Must not remove type parameter highlighter");
        }
      }
    });

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    type("//xxx");
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    backspace();
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    backspace();
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    backspace();
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    backspace();
    backspace();
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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

    List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    assertSize(1, infos);
    assertEquals("Variable 'local' is never used", infos.getFirst().getDescription());

    type("local");

    infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    assertSize(1, infos);
    assertEquals("Field 'cons' is never used", infos.getFirst().getDescription());
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

    assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    type("//my comment inside method body, so class modifier won't be visited");
    assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }

  public void testWhenTypingOverWrongReferenceIncludingRightAfterTheEndAndRightBeforeStartItsColorMustStayTheRedWithoutAnyBlinking() {
    configureByText(JavaFileType.INSTANCE, """
      class S {  int f() {
          return asfsdfsdfsd<caret>;
      }}""");

    HighlightInfo error = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    assertSame(HighlightInfoType.WRONG_REF, error.type);

    Document document = getDocument(getFile());

    type("xxx");

    // right after typing, before the highlighting kicked in, its color must stay red
    for (HighlightInfo info : DaemonCodeAnalyzerImpl.getHighlights(document, HighlightInfoType.SYMBOL_TYPE_SEVERITY, getProject())) {
      if (TextRange.create(info).intersects(error)) {
        assertSame(HighlightInfoType.WRONG_REF, info.type);
        assertEquals("asfsdfsdfsd" + "xxx", info.getText());
      }
    }

    getEditor().getCaretModel().moveToOffset(error.startOffset);
    type("zzz");
    for (HighlightInfo info : DaemonCodeAnalyzerImpl.getHighlights(document, HighlightInfoType.SYMBOL_TYPE_SEVERITY, getProject())) {
      if (TextRange.create(info).intersects(error)) {
        assertSame(HighlightInfoType.WRONG_REF, info.type);
        assertEquals("zzz" + "asfsdfsdfsd" + "xxx", info.getText());
      }
    }

    HighlightInfo error2 = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    assertSame(HighlightInfoType.WRONG_REF, error2.type);
  }


  public void testQuickFixRemainsAvailableAfterAnotherFixHasBeenAppliedInTheSameCodeBlockBefore() throws Exception {
    configureByFile(BASE_PATH + "QuickFixes.java");

    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean old = settings.isNextErrorActionGoesToErrorsFirst();
    settings.setNextErrorActionGoesToErrorsFirst(true);

    try {
      Collection<HighlightInfo> errors = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
      assertSize(3, errors);
      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());

      List<IntentionAction> fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
      IntentionAction fix = assertContainsOneOf(fixes, "Delete catch for 'java.io.IOException'");

      IntentionAction finalFix = fix;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> finalFix.invoke(getProject(), getEditor(), getFile()));

      errors = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
      assertSize(2, errors);

      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());
      fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
      fix = assertContainsOneOf(fixes, "Delete catch for 'java.io.IOException'");

      IntentionAction finalFix1 = fix;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> finalFix1.invoke(getProject(), getEditor(), getFile()));

      assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());
      fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
      fix = assertContainsOneOf(fixes, "Delete catch for 'java.io.IOException'");

      IntentionAction finalFix2 = fix;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> finalFix2.invoke(getProject(), getEditor(), getFile()));

      assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    MarkupModel markup = DocumentMarkupModel.forDocument(myEditor.getDocument(), getProject(), true);
    TextRange[] highlightersBefore = getHighlightersTextRange(markup);

    type("%%%%");
    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    backspace();
    backspace();
    backspace();
    backspace();
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    TextRange[] highlightersAfter = getHighlightersTextRange(markup);

    assertSize(highlightersBefore.length, highlightersAfter);
    for (int i = 0; i < highlightersBefore.length; i++) {
      TextRange before = highlightersBefore[i];
      TextRange after = highlightersAfter[i];
      assertEquals(before.getStartOffset(), after.getStartOffset());
      assertEquals(before.getEndOffset(), after.getEndOffset());
    }
  }

  private static TextRange @NotNull [] getHighlightersTextRange(@NotNull MarkupModel markup) {
    List<RangeHighlighter> highlighters = getAllValidHighlighters(markup);

    TextRange[] result = new TextRange[highlighters.size()];
    for (int i = 0; i < highlighters.size(); i++) {
      result[i] = ProperTextRange.create(highlighters.get(i));
    }
    return orderByHashCode(result); // markup.getAllHighlighters returns unordered array
  }

  private static List<RangeHighlighter> getAllValidHighlighters(@NotNull MarkupModel markup) {
    // there might be invalid RH returned from markup.getAllHighlighters() because they added/removed concurrently
    return ContainerUtil.filter(markup.getAllHighlighters(), h -> h.isValid());
  }

  private static <T extends Segment> T @NotNull [] orderByHashCode(T @NotNull [] highlighters) {
    Arrays.sort(highlighters, (o1, o2) -> o2.hashCode() - o1.hashCode());
    return highlighters;
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
    HighlightInfo error = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    assertEquals("'}' expected", error.getDescription());

    type("//comment");
    HighlightInfo error2 = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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

    List<HighlightInfo> errs = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    assertSize(2, errs);
    assertEquals("'}' expected", errs.getFirst().getDescription());

    undo();
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }

  // todo - StoreUtil.saveDocumentsAndProjectsAndApp cannot save in EDT. If it is called in EDT,
  //  in this case, task is done under a modal progress, so, no idea how to fix the test, except executing it not in EDT (as it should be)
  public void _testDaemonIgnoresFrameDeactivation() {
    // return default value to avoid unnecessary save
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    String text = "class S { ArrayList<caret>XXX x;}";
    configureByText(JavaFileType.INSTANCE, text);
    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    GeneralSettings settings = GeneralSettings.getInstance();
    boolean frameSave = settings.isSaveOnFrameDeactivation();
    settings.setSaveOnFrameDeactivation(true);
    StoreUtilKt.runInAllowSaveMode(true, () -> {
      try {
        StoreUtil.saveDocumentsAndProjectsAndApp(false);

        try {
          checkDaemonReaction(false, () -> StoreUtil.saveDocumentsAndProjectsAndApp(false));
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
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

    List<HighlightInfo> warns = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    assertOneElement(warns);
    Editor editor = getEditor();
    List<HighlightInfo.IntentionActionDescriptor> actions =
      ShowIntentionsPass.getAvailableFixes(editor, getFile(), -1, ((EditorEx)editor).getExpectedCaretOffset());
    HighlightInfo.IntentionActionDescriptor descriptor = assertOneElement(actions);
    CodeInsightTestFixtureImpl.invokeIntention(descriptor.getAction(), getFile(), getEditor());

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    assertEmpty(ShowIntentionsPass.getAvailableFixes(editor, getFile(), -1, ((EditorEx)editor).getExpectedCaretOffset()));
  }


  public void testApplyErrorInTheMiddle() {
    @Language("JAVA")
    String text = "class <caret>X { " + """
      
          {
      //    String x = "<zzzzzzzzzz/>";
          }""".repeat(100) +
                  "\n}";
    configureByText(JavaFileType.INSTANCE, text);

    ((EditorImpl)myEditor).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    type("//");
    List<HighlightInfo> errors = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    assertSize(2, errors);

    backspace();
    backspace();

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }


  public void testErrorInTheEndOutsideVisibleArea() {
    String text = "<xml> \n" + StringUtil.repeatSymbol('\n', 1000) + "</xml>\nxxxxx<caret>";
    configureByText(XmlFileType.INSTANCE, text);

    ProperTextRange visibleRange = makeEditorWindowVisible(new Point(0, 1000), myEditor);
    assertTrue(visibleRange.getStartOffset() > 0);

    HighlightInfo info = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    assertEquals("Top level element is not completed", info.getDescription());

    type("xxx");
    info = assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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

    List<HighlightInfo> infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertSize(4, infos);

    type('\n');
    infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertSize(4, infos);

    deleteLine();

    infos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    assertSize(4, infos);
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

    assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    type(';');
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }



  public void testCancelsItSelfOnTypingInAlienProject() throws Throwable {
    String body = StringUtil.repeat("\"String field = null;\"\n", 1000);
    configureByText(JavaFileType.INSTANCE, "class X{ void f() {" + body + "<caret>\n} }");

    Project alienProject = PlatformTestUtil.loadAndOpenProject(createTempDirectory().toPath().resolve("alien.ipr"), getTestRootDisposable());

    DaemonProgressIndicator.runInDebugMode(() -> {
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
        myDaemonCodeAnalyzer.restart(getTestName(false));
        // start daemon in the main project. should check for its cancel when typing in alien
        AtomicBoolean checked = new AtomicBoolean();
        Runnable callbackWhileWaiting = () -> {
          if (!checked.getAndSet(true)) {
            typeInAlienEditor(alienEditor, 'x');
          }
        };
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), getEditor().getDocument(), callbackWhileWaiting);
      }
      catch (ProcessCanceledException ignored) {
        return;
      }
      fail("must throw PCE");
    });
  }

  public void testPasteInAnonymousCodeBlock() {
    @Language("JAVA")
    String text = """
      class X{ void f() {     int x=0;x++;
          Runnable r = new Runnable() { public void run() {
       <caret>
          }};
          <selection>int y = x;</selection>
      
      } }""";
    configureByText(JavaFileType.INSTANCE, text);
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_EDITOR_COPY);
    assertEquals("int y = x;", getEditor().getSelectionModel().getSelectedText());
    getEditor().getSelectionModel().removeSelection();
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_EDITOR_PASTE);
    assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }

  public void testPostHighlightingPassRunsOnEveryPsiModification() throws Exception {
    @Language("JAVA")
    String xText = "public class X { public static void ffffffffffffff(){} }";
    PsiFile x = createFile("X.java", xText);
    @Language("JAVA")
    String useText = """
      public class Use {
        { <caret>X.ffffffffffffff(); }
        public static void main(String[] args) { } // to avoid 'class Use never used'
      }""";
    PsiFile use = createFile("Use.java", useText);
    configureByExistingFile(use.getVirtualFile());

    enableDeadCodeInspection();

    Editor xEditor = createEditor(x.getVirtualFile());
    EditorTracker.getInstance(getProject()).setActiveEditorsInTests(List.of(xEditor, getEditor())); // EditorTrackerImpl does not work correctly in case of multiple editors in tests
    List<HighlightInfo> xInfos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), xEditor.getDocument(), HighlightSeverity.WARNING);
    HighlightInfo info = ContainerUtil.find(xInfos, xInfo -> xInfo.getDescription().equals("Method 'ffffffffffffff()' is never used"));
    assertNull(xInfos.toString(), info);

    Editor useEditor = myEditor;
    myDaemonCodeAnalyzer.restart(getTestName(false));
    List<HighlightInfo> useInfos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), useEditor.getDocument(), HighlightSeverity.WARNING);
    assertEmpty(useInfos);

    type('/');
    type('/');

    myDaemonCodeAnalyzer.restart(getTestName(false));
    xInfos = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), xEditor.getDocument(), HighlightSeverity.WARNING);
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

  public void testErrorDisappearsRightAfterTypingInsideVisibleAreaWhileDaemonContinuesToChugAlong_Stress() {
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

    HighlightInfo info = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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

    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    type("//");
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    backspace();
    backspace();
    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }

  public void testInterruptOnTyping_Stress() throws Throwable {
    @NonNls String filePath = "/psi/resolve/Thinlet.java";
    configureByFile(filePath);
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    myDaemonCodeAnalyzer.restart(getTestName(false));
    try {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      PsiFile psiFile = getFile();
      Project project = psiFile.getProject();
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
      myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), getEditor().getDocument(), () -> type(' '));
    }
    catch (Exception ignored) {
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

    PsiFile psiFile = getFile();
    Project project = psiFile.getProject();
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
    List<HighlightInfo> errors = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    assertEmpty(errors);
    List<HighlightInfo> initialWarnings = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
    assertEmpty(initialWarnings);
    int N_BLOCKS = codeBlocks(psiFile).size();
    assertTrue("codeblocks :"+N_BLOCKS, N_BLOCKS > 1000);
    Random random = new Random();
    int N = 10;
    // try with both serialized and not-serialized passes
    myDaemonCodeAnalyzer.serializeCodeInsightPasses(false);
    for (int i=0; i<N*2; i++) {
      PsiCodeBlock block = codeBlocks(psiFile).get(random.nextInt(N_BLOCKS));
      getEditor().getCaretModel().moveToOffset(block.getLBrace().getTextOffset() + 1);
      type("\n/*xxx*/");
      List<HighlightInfo> warnings = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING);
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
  private List<PsiCodeBlock> codeBlocks(@NotNull PsiFile psiFile) {
    List<PsiCodeBlock> blocks = new ArrayList<>();
    psiFile.accept(new JavaRecursiveElementWalkingVisitor() {
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
    FileEditor fileEditor2 = assertOneElement(FileEditorManager.getInstance(getProject()).openFile(myFile.getVirtualFile()));
    Editor editor2 = ((TextEditor)fileEditor2).getEditor();
    Disposer.register(getTestRootDisposable(), () -> FileEditorManager.getInstance(getProject()).closeFile(fileEditor2.getFile()));
    myDaemonCodeAnalyzer.restart(getTestName(false));
    applied.clear();
    collected.clear();
    myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), editor1.getDocument());
    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(editor1.getDocument(), null, myProject);
    assertEmpty(errors);

    assertEquals(collected, ContainerUtil.newHashSet(editor1, editor2));
    assertEquals(applied, ContainerUtil.newHashSet(editor1, editor2));
  }

  // checks that only one instance is running
  public static class MySingletonAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    private static final AtomicBoolean wait = new AtomicBoolean();
    private static final AtomicBoolean running = new AtomicBoolean();
    private static final String SWEARING = "No swearing";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (running.getAndSet(true)) {
        throw new IllegalStateException("Already running");
      }
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        while (wait.get()) {
          Thread.onSpinWait();
        }
        holder.newAnnotation(HighlightSeverity.ERROR, SWEARING).range(element).create();
        iDidIt();
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
      running.set(false);
    }
  }

  public void testTwoEditorsForTheSameDocumentDoNotCompeteForMarkupModelAndHighlightingDoesNotBlinkAfterModificationInSomeEditor() {
    Set<Editor> applied = ConcurrentCollectionFactory.createConcurrentSet();
    Set<Editor> collected = ConcurrentCollectionFactory.createConcurrentSet();
    registerFakePass(applied, collected);

    @Language("JAVA")
    String text = """
    class X {
      //XXX
      void foo() {
        blahblah(); <caret>
      }
    }
    """;
    text = text.replaceAll("blahblah\\(\\); <caret>\n", "blahblah(); <caret>\n"+"blahblah();\n".repeat(1000));
    assertTrue(text.length()>1000);
    configureByText(JavaFileType.INSTANCE, text);
    assertTrue(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR).size() > 1000);// unresolved references
    Editor editor1 = getEditor();
    Editor editor2 = EditorFactory.getInstance().createEditor(editor1.getDocument(),getProject());
    Disposer.register(getTestRootDisposable(), () -> EditorFactory.getInstance().releaseEditor(editor2));
    TextEditor textEditor1 = new PsiAwareTextEditorProvider().getTextEditor(editor1);
    TextEditor textEditor2 = new PsiAwareTextEditorProvider().getTextEditor(editor2);
    assertNotNull(textEditor1);
    assertNotNull(textEditor2);
    EditorTracker.getInstance(getProject()).setActiveEditorsInTests(List.of(editor1, editor2));

    // check that 'MySingletonAnnotator' is run only once for two editors for the same document
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MySingletonAnnotator()}, ()-> {
      for (int i=0; i<10; i++) {
        MySingletonAnnotator.wait.set(true);
        type("/");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> MySingletonAnnotator.wait.set(false), 1000, TimeUnit.MILLISECONDS);
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), editor1.getDocument());

        // revert back
        backspace();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), editor1.getDocument());
      }
    });
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
      Disposer.register(getTestRootDisposable(), () -> EditorFactory.getInstance().releaseEditor(editor2));
      setActiveEditors(editor1, editor2);

      myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), getEditor().getDocument());

      assertSameElements(collected, Arrays.asList(editor1, editor2));
      assertSameElements(applied, Arrays.asList(editor1, editor2));

      applied.clear();
      collected.clear();
      setActiveEditors(editor1, editor2);
      type("/* xxx */");
      myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myProject, myEditor.getDocument());

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
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        return new EditorBoundHighlightingPass(editor, psiFile, false) {
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
    runHeavyProcessing = false;
    try {
      Set<Editor> applied = Collections.synchronizedSet(new HashSet<>());
      Set<Editor> collected = Collections.synchronizedSet(new HashSet<>());
      registerFakePass(applied, collected);

      configureByText(PlainTextFileType.INSTANCE, "");
      Editor editor = getEditor();
      while (HeavyProcessLatch.INSTANCE.isRunning()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      type("xxx"); // restart daemon
      assertTrue(EditorTracker.Companion.getInstance(myProject).getActiveEditors().contains(editor));
      assertSame(editor, FileEditorManager.getInstance(myProject).getSelectedTextEditor());

      // wait for the first pass to complete
      long start = System.currentTimeMillis();
      while (myDaemonCodeAnalyzer.isRunning() || !applied.contains(editor)) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        if (System.currentTimeMillis() - start > 1000000) {
          fail("Too long waiting for daemon (" +(System.currentTimeMillis() - start)+"ms) ");
        }
      }

      runHeavyProcessing = true;
      ApplicationManager.getApplication().executeOnPooledThread(() ->
        HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Processing, "my own heavy op", ()-> {
          while (runHeavyProcessing) {
            Thread.onSpinWait();
          }
        })
      );
      while (!HeavyProcessLatch.INSTANCE.isRunning()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      applied.clear();
      collected.clear();

      type("xxx"); // try to restart daemon

      start = System.currentTimeMillis();
      while (System.currentTimeMillis() < start + 5000) {
        assertEmpty(applied);  // it should not restart
        assertEmpty(collected);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
    }
    finally {
      runHeavyProcessing = false;
    }
  }

  public void testDaemonDoesNotDisableItselfDuringVFSRefresh() {
    runHeavyProcessing = false;
    try {
      Set<Editor> applied = Collections.synchronizedSet(new HashSet<>());
      Set<Editor> collected = Collections.synchronizedSet(new HashSet<>());
      registerFakePass(applied, collected);

      configureByText(PlainTextFileType.INSTANCE, "");
      Editor editor = getEditor();
      while (HeavyProcessLatch.INSTANCE.isRunning()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      type("xxx"); // restart daemon
      assertTrue(EditorTracker.Companion.getInstance(myProject).getActiveEditors().contains(editor));
      assertSame(editor, FileEditorManager.getInstance(myProject).getSelectedTextEditor());


      // wait for the first pass to complete
      long start = System.currentTimeMillis();
      while (myDaemonCodeAnalyzer.isRunning() || !applied.contains(editor)) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        if (System.currentTimeMillis() - start > 1000000) {
          fail("Too long waiting for daemon (" +(System.currentTimeMillis() - start)+"ms) ");
        }
      }

      runHeavyProcessing = true;
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() ->
        HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Syncing, "my own vfs refresh", () -> {
          while (runHeavyProcessing) {
            Thread.onSpinWait();
          }
        })
      );
      while (!HeavyProcessLatch.INSTANCE.isRunning()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      applied.clear();
      collected.clear();

      type("xxx"); // try to restart daemon

      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
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
  }

  public void testDaemonMustDisableItselfDuringDocumentBulkModification() {
    configureByText(PlainTextFileType.INSTANCE, "");
    Editor editor = getEditor();

    Set<Editor> applied = Collections.synchronizedSet(new HashSet<>());
    Set<Editor> collected = Collections.synchronizedSet(new HashSet<>());
    DocumentUtil.executeInBulk(editor.getDocument(), () -> {
      registerFakePass(applied, collected);

      assertTrue(EditorTracker.Companion.getInstance(myProject).getActiveEditors().contains(editor));
      assertSame(editor, FileEditorManager.getInstance(myProject).getSelectedTextEditor());

      applied.clear();
      collected.clear();

      myDaemonCodeAnalyzer.restart(getTestName(false)); // try to restart daemon

      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() < start + 5000) {
        assertEmpty(applied);  // it must not restart
        assertEmpty(collected);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
    });

    applied.clear();
    collected.clear();

    myDaemonCodeAnalyzer.restart(getTestName(false)); // try to restart daemon

    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() < start + 5000 && applied.isEmpty()) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
    assertNotEmpty(applied);  // it must restart outside bulk
    assertNotEmpty(collected);
  }

  public void testModificationInsideCodeBlockDoesNotRehighlightWholeFile() {
    configureByText(JavaFileType.INSTANCE, """
      class X {
        int f = "error";
        int f() {
          return 11<caret>;
        }
      }""");
    HighlightInfo error = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    assertEquals("Incompatible types. Found: 'java.lang.String', required: 'int'", error.getDescription());

    error.getHighlighter().dispose();

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    type("23");
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    myEditor.getCaretModel().moveToOffset(0);
    type("/* */");
    HighlightInfo error2 = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    assertEquals("Incompatible types. Found: 'java.lang.String', required: 'int'", error2.getDescription());
  }

  public void testCodeFoldingPassRestartsOnRegionUnfolding() {
    @Language("JAVA")
    String text = """
      class Foo {
          void m() {

          }
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    EditorTestUtil.buildInitialFoldingsInBackground(myEditor);
    myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myProject, myEditor.getDocument());
    EditorTestUtil.executeAction(myEditor, IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myProject, myEditor.getDocument());
    checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

    WriteCommandAction.runWriteCommandAction(myProject, () -> myEditor.getDocument().insertString(0, "/*"));
    myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myProject, myEditor.getDocument());
    checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

    EditorTestUtil.executeAction(myEditor, IdeActions.ACTION_EXPAND_ALL_REGIONS);
    myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myProject, myEditor.getDocument());
    checkFoldingState("[FoldRegion -(0:37), placeholder='/.../']");
  }

  public void testChangingSettingsHasImmediateEffectOnOpenedEditor() {
    @Language("JAVA")
    String text = """
      class C {\s
        void m() {
        }\s
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    EditorTestUtil.buildInitialFoldingsInBackground(myEditor);
    myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myProject, myEditor.getDocument());
    checkFoldingState("[FoldRegion -(22:27), placeholder='{}']");

    JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
    boolean savedValue = settings.isCollapseMethods();
    try {
      settings.setCollapseMethods(true);
      CodeFoldingConfigurable.Util.applyCodeFoldingSettingsChanges();
      myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myProject, myEditor.getDocument());
      checkFoldingState("[FoldRegion +(22:27), placeholder='{}']");
    }
    finally {
      settings.setCollapseMethods(savedValue);
    }
  }

  @RequiresEdt
  private void checkFoldingState(String expected) {
    assertEquals(expected, Arrays.toString(myEditor.getFoldingModel().getAllFoldRegions()));
  }

  public void testRehighlightInDebuggerExpressionFragment() {
    PsiExpressionCodeFragment fragment = JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("+ <caret>\"a\"", null,
                                    PsiType.getJavaLangObject(getPsiManager(), GlobalSearchScope.allScope(getProject())), true);
    configureByExistingFile(fragment.getVirtualFile());

    ProperTextRange visibleRange = makeEditorWindowVisible(new Point(0, 0), myEditor);
    Document document = getEditor().getDocument();
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

      myDaemonCodeAnalyzer.restart(getTestName(false));

      HighlightInfo error = assertOneElement(
        myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
      assertEquals("Operator '+' cannot be applied to 'java.lang.String'", error.getDescription());

      type(" ");

      HighlightInfo after = assertOneElement(
        myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
      assertEquals("Operator '+' cannot be applied to 'java.lang.String'", after.getDescription());
    }
    finally {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
  }

  public void testDumbAwareHighlightingPassesStartEvenInDumbMode() {
    List<TextEditorHighlightingPassFactory> collected = Collections.synchronizedList(new ArrayList<>());
    List<TextEditorHighlightingPassFactory> applied = Collections.synchronizedList(new ArrayList<>());
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
        public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
          return new EditorBoundHighlightingPass(editor, psiFile, false) {
            @Override
            public void doCollectInformation(@NotNull ProgressIndicator progress) {
              if (DumbService.isDumb(myProject)) throw IndexNotReadyException.create();
              collected.add(SmartFac.this);
            }

            @Override
            public void doApplyInformationToEditor() {
              if (DumbService.isDumb(myProject)) return;
              applied.add(SmartFac.this);
            }
          };
        }
      }
    SmartFac smartFac = new SmartFac();
    registrar.registerTextEditorHighlightingPass(smartFac, null, null, false, -1);

    configureByText(PlainTextFileType.INSTANCE, "");
    myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    assertSameElements(collected, dumbFac, smartFac);
    assertSameElements(applied, dumbFac, smartFac);
    collected.clear();
    applied.clear();

    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, getTestRootDisposable());
    DumbModeTestUtils.runInDumbModeSynchronously(myProject, () -> {
      type(' ');
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);

      TextEditorHighlightingPassFactory f = assertOneElement(collected);
      assertSame(dumbFac, f);
      TextEditorHighlightingPassFactory f2 = assertOneElement(applied);
      assertSame(dumbFac, f2);
    });
  }

  public void testUncommittedByAccidentNonPhysicalDocumentMustNotHangDaemon() {
    ThreadingAssertions.assertEventDispatchThread();
    configureByText(JavaFileType.INSTANCE, "class X { void f() { <caret> } }");
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
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
    myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myProject, myEditor.getDocument());
    assertNotEmpty(DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.ERROR, getProject()));
    assertEquals(text, document.getText());  // retain non-phys document until after highlighting
    assertFalse(PsiDocumentManager.getInstance(myProject).isCommitted(document));
    assertTrue(PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments());
  }

  public void testPutArgumentsOnSeparateLinesIntentionMustNotRemoveErrorHighlighting() {
    configureByText(JavaFileType.INSTANCE, "class X{ static void foo(String s1, String s2, String s3) { foo(\"1\", 1.2, \"2\"<caret>); }}");
    assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));

    List<IntentionAction> fixes = LightQuickFixTestCase.getAvailableActions(getEditor(), getFile());
    IntentionAction intention = assertContainsOneOf(fixes, "Put arguments on separate lines");
    assertNotNull(intention);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> intention.invoke(getProject(), getEditor(), getFile()));
    assertOneElement(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }


  public void testHighlightingPassesAreInstantiatedOutsideEDTToImproveResponsiveness() throws Throwable {
    AtomicReference<Throwable> violation = new AtomicReference<>();
    AtomicBoolean applied = new AtomicBoolean();
    class MyCheckingConstructorTraceFac implements TextEditorHighlightingPassFactory {
      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
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
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    assertTrue(applied.get());
    if (violation.get() != null) {
      throw violation.get();
    }
  }

  private static class EmptyPassFactory implements TextEditorHighlightingPassFactory {
    @Override
    public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
      return new EmptyPass(psiFile.getProject(), editor.getDocument());
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
      assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    });
    // non-direct cycle
    assertThrows(IllegalArgumentException.class, () -> {
      registrar.reRegisterFactories(); // clear caches from incorrect factories above
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId2}, null, false, forcedId1);
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId3}, null, false, forcedId2);
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId3);
      assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    });

    registrar.reRegisterFactories(); // clear caches from incorrect factories above
    registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), null, null, false, forcedId1);
    registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId3);
    registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId3}, null, false, forcedId2);
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }

  public void testHighlightersMustDisappearWhenTheHighlightingIsSwitchedOff() {
    @Language("JAVA")
    String text = """
      class X {
        blah blah
        )(@*$)(*%@$)
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    assertNotEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    HighlightingSettingsPerFile.getInstance(getProject()).setHighlightingSettingForRoot(getFile(), FileHighlightingSetting.SKIP_HIGHLIGHTING);

    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
  }

  public void testTypingErrorElementMustHighlightIt() {
    ThreadingAssertions.assertEventDispatchThread();
    configureByText(JavaFileType.INSTANCE, "class X { void f() { } }<caret>");
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    makeEditorWindowVisible(new Point(0, 1000), myEditor);

    type("/");
    myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myProject, myEditor.getDocument());
    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.ERROR, getProject());
    assertNotEmpty(errors);
    assertTrue(errors.toString().contains("'class' or 'interface' expected"));
  }

  public void testTypingInsideCodeBlockCanAffectUnusedDeclarationInTheOtherClass() {
    enableInspectionTool(new UnusedSymbolLocalInspection());
    enableDeadCodeInspection();
    configureByFiles(null, BASE_PATH+getTestName(true)+"/p2/A2222.java", BASE_PATH+getTestName(true)+"/p1/A1111.java");
    assertEquals("A2222.java", getFile().getName());

    HighlightInfo info = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING));
    assertEquals("Class 'A2222' is never used", info.getDescription());

    Document document1111 = getFile().getParent().findFile("A1111.java").getFileDocument();
    // uncomment (inside code block) the reference to A2222
    WriteCommandAction.writeCommandAction(myProject).run(()->document1111.deleteString(document1111.getText().indexOf("//"), document1111.getText().indexOf("//")+2));

    // now A2222 is no longer unused
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING));
  }

  // test the other type of PSI change: child remove/child add
  public void testTypingInsideCodeBlockCanAffectUnusedDeclarationInTheOtherClass2() {
    enableInspectionTool(new UnusedSymbolLocalInspection());
    enableDeadCodeInspection();
    configureByFiles(null, BASE_PATH+getTestName(true)+"/p1/A1111.java", BASE_PATH+getTestName(true)+"/p2/A2222.java");
    assertEquals("A1111.java", getFile().getName());
    makeEditorWindowVisible(new Point(0, 1000), myEditor);
    HighlightInfo info = assertOneElement(
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING));
    assertEquals("Method 'foo()' is never used", info.getDescription());

    Document document2222 = getFile().getParent().findFile("A2222.java").getFileDocument();
    // uncomment (inside code block) the reference to A1111
    WriteCommandAction.writeCommandAction(myProject).run(()->document2222.deleteString(document2222.getText().indexOf("//"), document2222.getText().indexOf("//")+2));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    // now foo() is no longer unused
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.WARNING));
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
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    MarkupModel markupModel = DocumentMarkupModel.forDocument(myEditor.getDocument(), getProject(), true);
    for (int i=0; i<10; i++) {
      type(" // TS");
      assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
      assertEmpty(getErrorsFromMarkup(markupModel));

      backspace();backspace();backspace();backspace();backspace();backspace();
      assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
      assertEmpty(getErrorsFromMarkup(markupModel));
    }
  }

  // highlights all //XXX, but very slow
  public static class MyVerySlowAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    private static final AtomicBoolean wait = new AtomicBoolean();
    private static final String SWEARING = "No swearing";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      long start = System.currentTimeMillis();
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        while (wait.get()) {
          Thread.onSpinWait();
        }
        holder.newAnnotation(HighlightSeverity.ERROR, SWEARING).range(element).create();
        iDidIt();
      }
      LOG.debug(getClass().getSimpleName()+".annotate("+element+") = "+didIDoIt()+" ("+(System.currentTimeMillis()-start)+"ms)");
    }
    private static List<HighlightInfo> myHighlights(MarkupModel markupModel) {
      return getAllValidHighlighters(markupModel)
        .stream()
        .map(highlighter -> HighlightInfo.fromRangeHighlighter(highlighter))
        .filter(Objects::nonNull)
        .filter(info -> SWEARING.equals(info.getDescription())).toList();
    }
  }
  private static List<HighlightInfo> highlightsWithDescription(MarkupModel markupModel, String description) {
    return getAllValidHighlighters(markupModel)
      .stream()
      .map(highlighter -> HighlightInfo.fromRangeHighlighter(highlighter))
      .filter(Objects::nonNull)
      .filter(info -> description.equals(info.getDescription())).toList();
  }

  public void testInvalidPSIElementsCreatedByTypingNearThemMustBeRemovedImmediatelyMeaningLongBeforeTheHighlightingPassFinished() {
    //setTraceDaemonLoggerLevel();
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
      
          <caret> : // Unexpected token
      
        }
      }""";

    assertInvalidPSIElementHighlightingIsRemovedImmediatelyAfterRepairingChange(text, "Unexpected token", () -> type("//"));
  }

  @RequiresEdt
  private void assertInvalidPSIElementHighlightingIsRemovedImmediatelyAfterRepairingChange(@Language("JAVA") String text,
                                                                                           String errorDescription,
                                                                                           Runnable repairingChange // the change which is supposed to fix the invalid PSI highlight
  ) {
    configureByText(JavaFileType.INSTANCE, text);
    makeEditorWindowVisible(new Point(0, 1000), myEditor);
    MyVerySlowAnnotator.wait.set(false);
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyVerySlowAnnotator()}, ()->{
      MarkupModel markupModel = DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
      assertNotEmpty(highlightsWithDescription(markupModel, errorDescription));
      assertNotEmpty(MyVerySlowAnnotator.myHighlights(markupModel));

      getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
          ReadAction.run(()->
          LOG.debug("daemonStarting("+fileEditors+"). errors=\n"+StringUtil.join(DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.ERROR, getProject()),"\n")));
        }

        @Override
        public void daemonFinished(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
          ReadAction.run(()->
          LOG.debug("daemonFinished("+fileEditors+"). errors=\n"+StringUtil.join(DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.ERROR, getProject()),"\n")));
        }

        @Override
        public void daemonCancelEventOccurred(@NotNull String reason) {
          ReadAction.run(()->
          LOG.debug("daemonCancelEventOccurred("+reason+"). errors=\n"+StringUtil.join(DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), HighlightSeverity.ERROR, getProject()),"\n")));
        }
      });
      MyVerySlowAnnotator.wait.set(true);
      repairingChange.run(); //repair invalid psi
      AtomicBoolean success = new AtomicBoolean();
      // register very slow annotator and make sure the invalid PSI highlighting was removed before this annotator finished
      TestTimeOut n = TestTimeOut.setTimeout(100, TimeUnit.SECONDS);
      Runnable checkHighlighted = () -> {
        if (highlightsWithDescription(markupModel, errorDescription).isEmpty() && MyVerySlowAnnotator.wait.get()) {
          // removed before highlighting is finished
          MyVerySlowAnnotator.wait.set(false);
          success.set(true);
          return;
        }
        if (n.isTimedOut()) {
          String dump = MyVerySlowAnnotator.wait + ThreadDumper.dumpThreadsToString();
          MyVerySlowAnnotator.wait.set(false);
          throw new RuntimeException(new TimeoutException(dump));
        }
      };
      try {
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), getEditor().getDocument(), checkHighlighted);
      }
      finally {
        MyVerySlowAnnotator.wait.set(false);
      }

      assertEmpty(highlightsWithDescription(markupModel, errorDescription));
      assertNotEmpty(MyVerySlowAnnotator.myHighlights(markupModel));
      assertTrue(success.get());
    });
  }

  @SuppressWarnings("FieldMayBeStatic")
  @Language(value = "JAVA", prefix="class X { void foo() {\n", suffix = "\n}\n}")
  private final String MANY_LAMBDAS_TEXT_TO_TYPE = """
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

  @SuppressWarnings("FieldMayBeStatic")
  @Language("JAVA")
  private final String MANY_LAMBDAS_INITIAL = """
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
  @SuppressWarnings("FieldMayBeStatic")
  @Language(value = "JAVA", prefix="class X { void foo() {\n", suffix = "\n}\n}")
  private final String LONG_LINE_WITH_PARENS_TEXT_TO_TYPE = """
    if (highlighter != null) highlighter += " text='" + StringUtil.first(getText(), 40, true) + "'";
    """;
  @SuppressWarnings("FieldMayBeStatic")
  @Language("JAVA")
  private final String LONG_LINE_WITH_PARENS_INITIAL_TEXT = """
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
  @RequiresEdt
  private void assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(String initialText, String textToType, Runnable afterWaitForDaemonToStart) {
    // run expensive consistency checks on each typing
    HighlightInfoUpdaterImpl updater = (HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(getProject());
    updater.runAssertingInvariants(() -> {
      String finalText = initialText.replace("<caret>", textToType);
      configureByText(JavaFileType.INSTANCE, finalText);
      assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
      for (int i=0; i<10; i++) {
        //System.out.println("i = " + i);
        PassExecutorService.LOG.debug("i = " + i);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().setText("  "));
        myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR); // reset various optimizations e.g. FileStatusMap.getCompositeDocumentDirtyRange
        MarkupModel markupModel = DocumentMarkupModel.forDocument(myEditor.getDocument(), getProject(), true);
        for (int c = 0; c < finalText.length(); c++) {
          PassExecutorService.LOG.debug("c = " + c);
          //System.out.println("  c = " + c);
          int o=c;
          //updater.assertNoDuplicates(myFile, getErrorsFromMarkup(markupModel), "errors from markup ");
          WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            assertFalse(myDaemonCodeAnalyzer.isRunning());
            long docStamp = myEditor.getDocument().getModificationStamp();
            char charToType = finalText.charAt(o);
            type(charToType);
            if (docStamp != myEditor.getDocument().getModificationStamp()) { // condition could be false when type handler does overtype ')' with already existing ')'
              assertFalse(myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile));
            }
          });
          //updater.assertNoDuplicates(myFile, getErrorsFromMarkup(markupModel), "errors from markup ");
          myDaemonCodeAnalyzer.restart(myFile, this);
          List<HighlightInfo> errorsFromMarkup = getErrorsFromMarkup(markupModel);
          //updater.assertNoDuplicates(myFile, errorsFromMarkup, "errors from markup ");
          //((HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(getProject())).assertMarkupDataConsistent(myFile);
          PassExecutorService.LOG.debug(" errorsfrommarkup:\n" + StringUtil.join(ContainerUtil.sorted(errorsFromMarkup, Segment.BY_START_OFFSET_THEN_END_OFFSET), "\n") + "\n-----\n");
          myTestDaemonCodeAnalyzer.waitForDaemonToStart(getProject(), myEditor.getDocument(), 30 * 1000);
          if (afterWaitForDaemonToStart != null) {
            afterWaitForDaemonToStart.run();
          }
        }
        // some chars might be inserted by TypeHandlers
        while (!myEditor.getDocument().getText().substring(myEditor.getCaretModel().getOffset()).isEmpty()) {
          delete(myEditor);
        }
        LOG.debug("All typing completed. " +
                  "\neditor text:-----------\n"+myEditor.getDocument().getText()+"\n-------\n"+
                  "errors in markup: " + StringUtil.join(getErrorsFromMarkup(markupModel), "\n") + "\n-----\n");
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), myEditor.getDocument());
        assertEmpty(myEditor.getDocument().getText(), getErrorsFromMarkup(markupModel));
      }
    });
  }

  private static @NotNull List<HighlightInfo> getErrorsFromMarkup(@NotNull MarkupModel model) {
    return getAllValidHighlighters(model)
      .stream()
      .map(m -> HighlightInfo.fromRangeHighlighter(m))
      .filter(Objects::nonNull)
      .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
      .toList();
  }
  private static void assertNoDuplicateInfosFromMarkup(@NotNull MarkupModel model) {
    List<HighlightInfo> infos = getAllValidHighlighters(model)
      .stream()
      .map(m -> HighlightInfo.fromRangeHighlighter(m))
      .filter(Objects::nonNull)
      .toList();
    Map<TextRange, List<HighlightInfo>> byRange = infos.stream().collect(Collectors.groupingBy(info -> TextRange.create(info)));
    for (List<HighlightInfo> errors : byRange.values()) {
      Set<String> set = ContainerUtil.map2Set(errors, e -> e.getDescription());
      if (set.size() != errors.size()) {
        fail("Duplicates: " + errors);
      }
    }
  }

  public void testMultiplePSIInvalidationsMustDelayTheirHighlightersRemovalForShortTimeToAvoidFlickering() {
    //IJPL-160136 Blinking highlighting on refactoring TS code

    @Language("JAVA")
    String text = """
      class X {
         int xxx;
         void foo() {
           for (int i=0; i<xxx+1; i++) {
              if (i == xxx) return xxx;
           }
         }
         public int hashCode() {
           return xxx;
         }
      }""";

    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyXXXIdentifierAnnotator()}, ()->{
      configureByText(JavaFileType.INSTANCE, text);
      makeEditorWindowVisible(new Point(0, 1000), myEditor);

      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
      assertSize(5, MyXXXIdentifierAnnotator.myHighlights(markupModel));

      List<String> events = Collections.synchronizedList(new ArrayList<>());
      markupModel.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
        @Override
        public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
          events.add("added " + highlighter);
        }

        @Override
        public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
          events.add("removed " + highlighter);
        }
      });
      // invalidate all xxx (leaving the text the same), check that these highlighters are recycled
      List<PsiIdentifier> identifiers = new ArrayList<>();
      getFile().accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitIdentifier(@NotNull PsiIdentifier identifier) {
          super.visitIdentifier(identifier);
          if (identifier.getText().equals("xxx")) {
            identifiers.add(identifier);
          }
        }
      });
      for (PsiIdentifier identifier : identifiers) {
        WriteCommandAction.writeCommandAction(getProject()).run(() -> identifier.replace(PsiElementFactory.getInstance(getProject()).createIdentifier("xxx")));
        assertFalse(identifier.isValid());
      }
      myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
      assertEmpty(events);
    });
  }
  // highlight all identifiers with text "xxx"
  public static class MyXXXIdentifierAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    private static final String MSG = "xxx?";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiIdentifier && element.getText().equals("xxx")) {
        holder.newAnnotation(HighlightSeverity.ERROR, MSG).range(element).create();
        iDidIt();
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
    }
    static List<HighlightInfo> myHighlights(MarkupModel markupModel) {
      return getAllValidHighlighters(markupModel)
        .stream()
        .map(highlighter -> HighlightInfo.fromRangeHighlighter(highlighter))
        .filter(Objects::nonNull)
        .filter(info -> MSG.equals(info.getDescription())).toList();
    }
  }

  public void testDaemonRestartsEventWhenCanceledDuringRunUpdateMethodCallIsRunning() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
    
      }
    """);
    Document document = getDocument(getFile());
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    // warmup highlighting first, calibrating before that would make little sense
    for (int i = 0; i < 10; i++) {
      type("x");
      myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), document);
      backspace();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
    long avgElapsedTime = 0;
    int CALIBRATE_N = 100;
    // compute time the highlighting takes to highlight this file completely
    for (int i = 0; i < CALIBRATE_N; i++) {
      type("x");
      long elapsed = TimeoutUtil.measureExecutionTime(() -> myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), document));
      avgElapsedTime += elapsed;
      backspace();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    avgElapsedTime /= CALIBRATE_N; // compute avg time the daemon takes to highlight this sample. then we use that time to delay in hope that DAI.runUpdate() is about to run
    LOG.debug("avgElapsedTime = " + avgElapsedTime);

    Random random = new Random();
    for (int i = 0; i < 1000; i++) {
      LOG.debug("i = " + i);
      type("x");
      long delay = random.nextLong(avgElapsedTime+1);
      Future<?> future = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
        myDaemonCodeAnalyzer.restart(getTestName(false));
        //String edtTrace = ThreadDumper.dumpEdtStackTrace(ThreadDumper.getThreadInfos());
        //System.out.println(edtTrace+"\n  delay ="+delay+"  --------------------------------");
      }, delay, TimeUnit.MILLISECONDS);
      while (!future.isDone()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      try {
        future.get();
      }
      catch (Exception e) {
        throw new AssertionError(e);
      }

      myTestDaemonCodeAnalyzer.waitForDaemonToStart(getProject(), document, 60_000);
      backspace();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
  }

  enum DEvent { STARTED, FINISHED, CANCELED }
  public void testDaemonListenerEventsMustBePairedEvenWhenModalitySuddenlyChangedHalfRoad() {
    configureByText(JavaFileType.INSTANCE, """
      class AClass<caret> {
    
      }
    """);
    Document document = getDocument(getFile());
    assertEmpty(myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR));
    List<Pair<DEvent,String>> eventLog = Collections.synchronizedList(new ArrayList<>());
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
      @Override
      public void daemonStarting(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
        eventLog.add(Pair.create(DEvent.STARTED,""));
      }

      @Override
      public void daemonFinished(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
        eventLog.add(Pair.create(DEvent.FINISHED, ""));
      }

      @Override
      public void daemonCancelEventOccurred(@NotNull String reason) {
        eventLog.add(Pair.create(DEvent.CANCELED, reason));
      }
    });
    for (int i=0; i<100; i++) {
      myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), document);
      eventLog.clear();
      Disposable disposable = Disposer.newDisposable();
      type("x");
      myTestDaemonCodeAnalyzer.waitForDaemonToStart(getProject(), document, 10_000);
      myDaemonCodeAnalyzer.disableUpdateByTimer(disposable);
      try {
        {
          long deadline = System.currentTimeMillis() + 10; // do something for awhile
          while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          }
        }

        type("y");
      }
      finally {
        Disposer.dispose(disposable); //reenable DCA
      }
      myTestDaemonCodeAnalyzer.waitForDaemonToFinish(getProject(), document);

      assertEventsArePaired(eventLog);
      backspace();
      backspace();
    }
  }

  private static void assertEventsArePaired(@NotNull List<? extends Pair<DEvent, String>> log) {
    String toString = log.toString();
    int starts = 0;
    int ends = 0;
    for (Pair<DEvent,String> e : log) {
      switch(e.getFirst()) {
        case STARTED:
          assertEquals(toString, ends,starts);
          starts++;
          break;
        case CANCELED:
        case FINISHED:
          assertTrue(toString, starts > 0);
          assertTrue(toString, starts >= ends);
          ends++;
          break;
        default:
          fail(toString);
      }
    }
    assertEquals(toString, starts, ends);
  }

  public void testFileOutsideProjectRootsMustNotRestartDaemonTooOften() throws ExecutionException, InterruptedException {
    VirtualFile root = createVirtualDirectoryForContentFile();
    VirtualFile virtualFile = createChildData(createChildDirectory(root, ".git"), "config");

    String text = "[xxx]\nblah-blah";
    setFileText(virtualFile, text);

    assertEquals(PlainTextFileType.INSTANCE, virtualFile.getFileType());
    PsiFile psiFile = getPsiManager().findFile(virtualFile);
    assertNull(String.valueOf(psiFile), psiFile);

    AtomicInteger restarts = new AtomicInteger();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            new DaemonCodeAnalyzer.DaemonListener() {
              @Override
              public void daemonStarting(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
                restarts.incrementAndGet();
              }
            });
    configureByExistingFile(virtualFile);

    TestTimeOut t = TestTimeOut.setTimeout(10, TimeUnit.SECONDS);
    while (!t.isTimedOut()) {
      assertTrue(restarts.toString(), restarts.get() < 10);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
  }


  public void testStartingTypingCommentMustExtendExistingHighlighterButShrinkItBackAfterRehighlighting() {
    @Language("JAVA")
    String text = """
      class X {
         void foo() {
           Str/*ing xxx;
         }
      }""";

    configureByText(JavaFileType.INSTANCE, text);
    List<HighlightInfo> errs = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    HighlightInfo info = ContainerUtil.find(errs, e -> "Cannot resolve symbol 'Str'".equals(e.getDescription()));
    assertTrue(errs.toString(), info != null && info.getText().equals("Str"));

    int i = getEditor().getDocument().getText().indexOf("/*");
    WriteCommandAction.writeCommandAction(getProject()).run(() -> getEditor().getDocument().insertString(i, "/*"));
    errs = myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    info = ContainerUtil.find(errs, e -> "Cannot resolve symbol 'Str'".equals(e.getDescription()));
    assertTrue(errs.toString(), info != null && info.getText().equals("Str"));
  }

  public void testGarbageCollectedFileViewProviderAtUnfortunateMomentMustNotLeadToDanglingRangeHighlighters() {
    @Language("JAVA")
    String text = """
      class X {
         void foo() {
           String xxx;
         }
      }""";

    configureByText(JavaFileType.INSTANCE, text);
    myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    String h1 = renderHighlighters(getSortedHighlights());

    GCWatcher tracking = GCWatcher.tracking(myFile);
    myFile = null;
    IntentionsUI.getInstance(myProject).invalidate(); // clear com.intellij.codeInsight.intention.impl.CachedIntentions.myProject
    tracking.ensureCollectedWithinTimeout(30_000);

    myFile = PsiDocumentManager.getInstance(myProject).getPsiFile(getEditor().getDocument());
    myDaemonCodeAnalyzer.restart(getTestName(false));
    myTestDaemonCodeAnalyzer.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
    String h2 = renderHighlighters(getSortedHighlights());

    assertEquals(h1, h2);
  }

  private static @NotNull String renderHighlighters(List<RangeHighlighter> highlighters) {
    return StringUtil.join(highlighters, h->h.getTextRange()+":"+h.getTextAttributes(EditorColorsUtil.getGlobalOrDefaultColorScheme()), "\n");
  }

  private List<RangeHighlighter> getSortedHighlights() {
    List<RangeHighlighter> highlighters = getAllValidHighlighters(DocumentMarkupModel.forDocument(myEditor.getDocument(), myProject, true));
    return ContainerUtil.sorted(highlighters, (o1, o2) -> {
      HighlightInfo h1 = HighlightInfo.fromRangeHighlighter(o1);
      HighlightInfo h2 = HighlightInfo.fromRangeHighlighter(o2);
      return h1 == null || h2 == null ? Segment.BY_START_OFFSET_THEN_END_OFFSET.compare(o1, o2) : HighlightInfoUpdaterImpl.BY_OFFSETS_AND_HASH_ERRORS_FIRST.compare(h1, h2);
    });
  }
  public static void setTraceDaemonLoggerLevel() {
    Logger.getInstance("#com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil").setLevel(LogLevel.TRACE);
    Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl").setLevel(LogLevel.TRACE);
    Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator").setLevel(LogLevel.TRACE);
    Logger.getInstance("#com.intellij.codeInsight.daemon.impl.FileStatusMap").setLevel(LogLevel.TRACE);
    Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass").setLevel(LogLevel.TRACE);
    Logger.getInstance("#com.intellij.codeInsight.daemon.impl.HighlightInfoUpdaterImpl").setLevel(LogLevel.TRACE);
    Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PassExecutorService").setLevel(LogLevel.TRACE);
    Logger.getInstance("#com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil").setLevel(LogLevel.TRACE);
    Logger.getInstance("#com.intellij.openapi.editor.impl.RangeHighlighterImpl").setLevel(LogLevel.TRACE);
    Logger.getInstance("#com.intellij.openapi.editor.impl.IntervalTreeImpl").setLevel(LogLevel.TRACE);
    // clear internal buffer
    //TestLoggerFactory.onFixturesInitializationStarted(true);
    //TestLoggerFactory.onTestStarted();
  }
}
