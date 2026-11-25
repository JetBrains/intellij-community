// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.application.options.editor.CodeFoldingConfigurable;
import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.EditorInfo;
import com.intellij.codeInsight.daemon.CanChangeDocumentDuringHighlighting;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.EditorTracker;
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightInfoUpdater;
import com.intellij.codeInsight.daemon.impl.HighlightInfoUpdaterImpl;
import com.intellij.codeInsight.daemon.impl.IntentionsUI;
import com.intellij.codeInsight.daemon.impl.PassExecutorService;
import com.intellij.codeInsight.daemon.impl.ProgressableTextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter;
import com.intellij.codeInsight.daemon.impl.TextEditorHighlightingPassRegistrarImpl;
import com.intellij.codeInsight.daemon.impl.VisibleHighlightingPassFactory;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInsight.multiverse.EditorContextManager;
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
import com.intellij.configurationStore.StoreUtil;
import com.intellij.configurationStore.StoreUtilKt;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.idea.TestFor;
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
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorModificationUtilEx;
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
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.project.ProjectKt;
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
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.inline.InlineRefactoringActionHandler;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.HeavyTestHelper;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.UITestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.EditorNotificationsImpl;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.TestTimeOut;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import kotlin.Unit;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.future.FutureKt;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Dimension;
import java.awt.Point;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesFixture.daemonRespondToChangesFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * tests general daemon behaviour/interruptibility/restart during highlighting
 */
@SkipSlowTestLocally
@CanChangeDocumentDuringHighlighting
@TestApplication
public class DaemonRespondToChangesTest {
  private static final Logger LOG = Logger.getInstance(DaemonRespondToChangesTest.class);
  public static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typing/";

  private final TestFixture<DaemonRespondToChangesFixture> daemonFixture =
    daemonRespondToChangesFixture(DaemonRespondToChangesTest::configureLocalInspectionTools);

  @BeforeEach
  void setUp() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.setTestLineMarkers(true);
    fixture.enableInspectionTool(new UnusedDeclarationInspection());
    UndoManager.getInstance(fixture.getProject());
  }

  private static LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {
      new FieldCanBeLocalInspection(),
      new RequiredAttributesInspectionBase(),
      new CheckDtdReferencesInspection(),
      new AccessStaticViaInstance(),
    };
  }

  private static <T> T assertOneItem(@NotNull Collection<? extends T> collection) {
    assertThat(collection).hasSize(1);
    return collection.iterator().next();
  }

  private static <T> T read(@NotNull ThrowableComputable<T, RuntimeException> computable) {
    return ReadAction.computeBlocking(computable);
  }

  private static void assertThrowsIllegalArgument(@NotNull ThrowableRunnable<?> runnable) {
    Throwable thrown = catchThrowable(runnable::run);
    while (thrown instanceof TestLoggerFactory.TestLoggerAssertionError && thrown.getCause() != null) {
      thrown = thrown.getCause();
    }
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * Waits on the EDT until the daemon finishes {@code psiFile}, and returns its highlighting with {@code minSeverity} or a higher severity.
   */
  private List<HighlightInfo> waitHighlighting(@NotNull PsiFile psiFile, @NotNull HighlightSeverity minSeverity) {
    TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = daemonFixture.get().getTestDaemonCodeAnalyzer();
    return EdtTestUtil.runInEdtAndGet(() -> testDaemonCodeAnalyzer.waitHighlighting(psiFile, minSeverity));
  }

  private void waitForDaemonToFinish(@NotNull PsiFile psiFile) {
    TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = daemonFixture.get().getTestDaemonCodeAnalyzer();
    EdtTestUtil.runInEdtAndWait(() -> testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile));
  }

  private static List<HighlightInfo> getHighlights(@NotNull Document document, HighlightSeverity minSeverity, @NotNull Project project) {
    return read(() -> DaemonCodeAnalyzerImpl.getHighlights(document, minSeverity, project));
  }

  /**
   * Opens {@code file} in a real editor and restarts the daemon, as {@code createEditor} of the JUnit 3 base did.
   */
  private Editor createEditor(@NotNull VirtualFile file) {
    Project project = daemonFixture.get().getProject();
    return EdtTestUtil.runInEdtAndGet(() -> {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      Editor editor = Objects.requireNonNull(FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, 0), false));
      ((EditorImpl)editor).setCaretActive();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      DaemonCodeAnalyzerEx.getInstanceEx(project).restart("DaemonRespondToChangesTest.createEditor " + file);
      return editor;
    });
  }

  private static void typeInAlienEditor(@NotNull Editor alienEditor, char c) {
    EditorActionManager.getInstance();
    TypedAction action = TypedAction.getInstance();
    DataContext dataContext = ((EditorEx)alienEditor).getDataContext();

    action.actionPerformed(alienEditor, c, dataContext);
  }

  @Test
  public void testTypingSpaceInsideError() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass {
            {
              toString(0,<caret>0);
            }
          }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);

      for (int i = 0; i < 100; i++) {
        fixture.type(" ");
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);
      }
    });
  }


  @Test
  public void testBackSpaceInsideError() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class E {
               void fff() {
                   int i = <caret>
               }
           }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);

      fixture.backspace();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);
    });
  }

  @Test
  public void testUnusedFieldUpdate() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
         class Unused {
           private int ffff;
           void foo(int p) {
             if (p==0) return;
             <caret>
           }
         }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Document document = fixture.getDocument(fixture.getFile());
      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).hasSize(1);
      assertEquals("Private field 'ffff' is never used", infos.getFirst().getDescription());

      fixture.type("  foo(ffff++);");
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      List<HighlightInfo> errors = getHighlights(document, HighlightSeverity.WARNING, fixture.getProject());
      assertThat(errors).isEmpty();
    });
  }

  @Test
  public void testUnusedMethodUpdate() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
            static void ffff() {}
            public static void main(String[] args){
                for (int i=0; i<1000;i++) {
                    System.out.println(i);
                    <caret>ffff();
                }
            }
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      fixture.enableInspectionTool(new UnusedDeclarationInspection(true));
      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).isEmpty();

      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE));
      infos = fixture.waitHighlighting(HighlightSeverity.WARNING);

      assertThat(infos).hasSize(1);
      assertEquals("Method 'ffff()' is never used", infos.getFirst().getDescription());
    });
  }


  @Test
  public void testAssignedButUnreadFieldUpdate() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.configureByFile(BASE_PATH + "AssignedButUnreadField.java");
      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).hasSize(1);
      assertEquals("Private field 'text' is assigned but never accessed", infos.getFirst().getDescription());

      fixture.ctrlW();
      Editor editor = fixture.getEditor();
      WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> EditorModificationUtilEx.deleteSelectedText(editor));
      fixture.type("  text");

      List<HighlightInfo> errors = fixture.waitHighlighting(HighlightSeverity.WARNING);
      PsiFile psiFile = fixture.getFile();
      assertThat(errors).as(read(() -> psiFile.getText())).isEmpty();
    });
  }

  @Test
  public void testDaemonIgnoresNonPhysicalEditor() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass<caret> {

          }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      EditorFactory editorFactory = EditorFactory.getInstance();
      Document consoleDoc = editorFactory.createDocument("my console blah");
      Editor editor = fixture.getEditor();
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        Editor consoleEditor = editorFactory.createEditor(consoleDoc);
        fixture.setActiveEditors(editor, consoleEditor);
        try {
          checkDaemonReaction(false, () -> fixture.caretRight(consoleEditor));
          checkDaemonReaction(true, () -> {
            boolean f1 = daemonCodeAnalyzer.isAllAnalysisFinished();
            typeInAlienEditor(consoleEditor, 'y');
            boolean f2 = daemonCodeAnalyzer.isAllAnalysisFinished();
            LOG.debug("f1 = " + f1+"; f2 = " + f2+"; "+daemonCodeAnalyzer.getUpdateProgress());
          });
          checkDaemonReaction(true, () -> fixture.backspace(consoleEditor));

          //real editor
          checkDaemonReaction(true, ()-> fixture.caretRight());
        }
        finally {
          editorFactory.releaseEditor(consoleEditor);
        }
      });
    });
  }

  @Test
  @TestFor(issues = "IJPL-260896")
  public void testCompletedFileRenewalDoesNotRetainEditor() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      var file = new LightVirtualFile("renew.txt", "text");
      var editorFactory = EditorFactory.getInstance();
      Project project = fixture.getProject();
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      AtomicReference<Editor> editorRef = new AtomicReference<>();
      Future<?> future = EdtTestUtil.runInEdtAndGet(() -> {
        var document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(file));
        var editor = editorFactory.createEditor(document, project);
        editorRef.set(editor);
        try {
          fixture.setActiveEditors(editor);
          testDaemonCodeAnalyzer.waitForTermination();
          Future<?> previousTask = getFileRenewalTask(daemonCodeAnalyzer);
          WriteAction.run(() -> PsiManagerEx.getInstanceEx(project).getFileManager().setViewProvider(file, null));
          assertNull(TextEditorBackgroundHighlighter.getCachedFileToHighlight(project, file, CodeInsightContexts.anyContext()));
          daemonCodeAnalyzer.restart(fixture.getTestName(false));
          PlatformTestUtil.waitWithEventsDispatching("The daemon must renew the file",
                                                     () -> getFileRenewalTask(daemonCodeAnalyzer) != previousTask, 10);
          Future<?> renewal = getFileRenewalTask(daemonCodeAnalyzer);
          PlatformTestUtil.waitWithEventsDispatching("The file renewal must finish", renewal::isDone, 10);
          renewal.get();
          return renewal;
        }
        finally {
          fixture.setActiveEditors();
          editorFactory.releaseEditor(editor);
        }
      });
      Editor editor = editorRef.get();
      LeakHunter.checkLeak(future, Editor.class, leaked -> leaked == editor);
    });
  }

  private static @NotNull Future<?> getFileRenewalTask(@NotNull DaemonCodeAnalyzerImpl daemonCodeAnalyzer) {
    return Objects.requireNonNull(
      ReflectionUtil.getField(DaemonCodeAnalyzerImpl.class, daemonCodeAnalyzer, Future.class, "renewInBackgroundAndRestart"));
  }

  public static class MyWaitingAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    private static final AtomicBoolean wait = new AtomicBoolean();
    private static volatile CountDownLatch inside = new CountDownLatch(1);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      long start = System.currentTimeMillis();
      inside.countDown();
      while (wait.get()) {
        Thread.onSpinWait();
      }
      iDidIt();
      LOG.debug(getClass().getSimpleName()+".annotate("+element+") = "+didIDoIt()+" ("+(System.currentTimeMillis()-start)+"ms)");
    }
  }
  @Test
  public void testDaemonMustRestartOnLoneWriteAction() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass<caret> {

          }
        """;
      MyWaitingAnnotator.wait.set(true);
      MyWaitingAnnotator.inside = new CountDownLatch(1);
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyWaitingAnnotator()}, ()-> {
        fixture.configureByText(JavaFileType.INSTANCE, text);
        while (!MyWaitingAnnotator.inside.await(1, TimeUnit.SECONDS)) {
          EdtTestUtil.runInEdtAndWait(() -> UIUtil.dispatchAllInvocationEvents());
        }
        EdtTestUtil.runInEdtAndWait(() -> {
          MyWaitingAnnotator.wait.set(false);
          WriteAction.run(()->{});
        });
        fixture.waitHighlighting(HighlightSeverity.ERROR);
      });
    });
  }
  @Test
  public void testDaemonMustNotHangOnLoneCommandExecution() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass<caret> {

          }
        """;
      MyWaitingAnnotator.wait.set(true);
      MyWaitingAnnotator.inside = new CountDownLatch(1);
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyWaitingAnnotator()}, ()-> {
        fixture.configureByText(JavaFileType.INSTANCE, text);
        while (!MyWaitingAnnotator.inside.await(1, TimeUnit.SECONDS)) {
          EdtTestUtil.runInEdtAndWait(() -> UIUtil.dispatchAllInvocationEvents());
        }
        EdtTestUtil.runInEdtAndWait(() -> {
          MyWaitingAnnotator.wait.set(false);
          CommandProcessor.getInstance().executeCommand(fixture.getProject(), ()->{
          }, "EditorChange", null);
        });

        fixture.waitHighlighting(HighlightSeverity.ERROR);
      });
    });
  }

  @Test
  public void testDaemonMustRestartOnlyOnceOnTyping() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass<caret> {

          }
        """;

      // make sure the highlighting is running, then type something, make sure it cancels only once
      List<String> restarts = ContainerUtil.createConcurrentList();
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyWaitingAnnotator()}, ()-> {
        MyWaitingAnnotator.wait.set(false);
        MyWaitingAnnotator.inside = new CountDownLatch(1);
        fixture.configureByText(JavaFileType.INSTANCE, text);
        fixture.waitHighlighting(HighlightSeverity.ERROR);

        MyWaitingAnnotator.inside = new CountDownLatch(1);
        MyWaitingAnnotator.wait.set(true);
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        while (!MyWaitingAnnotator.inside.await(1, TimeUnit.SECONDS)) {
          EdtTestUtil.runInEdtAndWait(() -> UIUtil.dispatchAllInvocationEvents());
        }
        fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
          @Override
          public void daemonCancelEventOccurred(@NotNull String reason) {
            restarts.add(ExceptionUtil.getThrowableText(new Throwable(reason)));
          }
        });
        EdtTestUtil.runInEdtAndWait(() -> {
          MyWaitingAnnotator.wait.set(false);
          fixture.type('X');
        });
        fixture.waitHighlighting(HighlightSeverity.ERROR);
      });
      assertThat(restarts).hasSize(1);
    });
  }


  @Test
  public void testDaemonIgnoresConsoleActivities() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass<caret> {

          }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      Project project = fixture.getProject();
      EdtTestUtil.runInEdtAndWait(() -> {
        ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

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
      });
    });
  }

  /**
   * Restarts the daemon, runs {@code action} on the EDT when the daemon starts, and checks whether the daemon cancels itself.
   * The whole check runs in one EDT block.
   */
  private void checkDaemonReaction(boolean mustCancelItself, @NotNull Runnable action) {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    PsiFile psiFile = fixture.getFile();
    Project project = fixture.getProject();
    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
    TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
    String testName = fixture.getTestName(false);
    EdtTestUtil.runInEdtAndWait(() -> {
      testDaemonCodeAnalyzer.waitHighlighting(psiFile, HighlightSeverity.ERROR);
      testDaemonCodeAnalyzer.waitForTermination();

      AtomicBoolean ran = new AtomicBoolean();
      Disposable disposable = Disposer.newDisposable();
      AtomicReference<RuntimeException> stopDaemonReason = new AtomicReference<>();
      project.getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
          new DaemonCodeAnalyzer.DaemonListener() {
            @Override
            public void daemonCancelEventOccurred(@NotNull String reason) {
              RuntimeException e = new RuntimeException("Some bastard's restarted daemon: " + reason);
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
        daemonCodeAnalyzer.restart(testName);
        try {
          testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile, ()->{});
        }
        catch (ProcessCanceledException ignored) {
        }
        assertTrue(ran.get());
        if (mustCancelItself) {
          assertNotNull(stopDaemonReason.get());
        }
        else {
          if (stopDaemonReason.get() != null) {
            throw stopDaemonReason.get();
          }
        }
      }
      finally {
        Disposer.dispose(disposable);
      }
    });
  }

  @Test
  public void testChangeXmlIncludeLeadsToRehighlight() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      LanguageFilter[] extensions = XMLLanguage.INSTANCE.getLanguageExtensions();
      for (LanguageFilter extension : extensions) {
        XMLLanguage.INSTANCE.unregisterLanguageExtension(extension);
      }

      List<PsiFile> retained;
      try {
        String location = fixture.getTestName(false) + ".xsd";
        final String url = "http://myschema/";
        EdtTestUtil.runInEdtAndWait(() -> ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable()));

        fixture.configureByFiles(null, BASE_PATH + fixture.getTestName(false) + ".xml", BASE_PATH + fixture.getTestName(false) + ".xsd");
        Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
        fixture.setActiveEditors(allEditors);
        Project project = fixture.getProject();
        retained = read(() -> {
          List<PsiFile> files = new ArrayList<>();
          for (Editor editor : allEditors) {
            Document document = editor.getDocument();
            PsiFile psiFile = Objects.requireNonNull(PsiDocumentManager.getInstance(project).getPsiFile(document));
            files.add(psiFile);
          }
          return files;
        });
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

        Editor schemaEditor = read(() -> {
          for (Editor editor : allEditors) {
            Document document = editor.getDocument();
            PsiFile psiFile = Objects.requireNonNull(PsiDocumentManager.getInstance(project).getPsiFile(document));
            if (location.equals(psiFile.getName())) {
              return editor;
            }
          }
          return null;
        });
        fixture.executeAction(IdeActions.ACTION_EDITOR_DELETE, Objects.requireNonNull(schemaEditor));

        List<HighlightInfo> errors = fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertThat(errors).isNotEmpty();
      }
      finally {
        for (LanguageFilter extension : extensions) {
          XMLLanguage.INSTANCE.registerLanguageExtension(extension);
        }
      }
      Reference.reachabilityFence(retained);
    });
  }


  @Test
  public void testRehighlightInnerBlockAfterInline() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.configureByFile(BASE_PATH + fixture.getTestName(false) + ".java");

      HighlightInfo error = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      assertEquals("Variable 'e' is already defined in the scope", error.getDescription());
      Editor editor = fixture.getEditor();
      PsiFile psiFile = fixture.getFile();
      EdtTestUtil.runInEdtAndWait(() -> {
        PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset()).getParent();

        DataContext dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT, element, ((EditorEx)editor).getDataContext());
        new InlineRefactoringActionHandler().invoke(fixture.getProject(), editor, psiFile, dataContext);
      });

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
    });
  }


  @Test
  public void testRangeMarkersDoNotGetAddedOrRemovedWhenUserIsJustTypingInsideHighlightedRegionAndEspeciallyInsideInjectedFragmentsWhichAreColoredGreenAndUsersComplainEndlesslyThatEditorFlickersThere() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class S { int f() {
            return <caret>hashCode();
        }}""";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      Collection<HighlightInfo> infos = fixture.waitHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
      assertThat(infos).hasSize(3);

      AtomicInteger count = new AtomicInteger();
      Document document = fixture.getDocument(fixture.getFile());
      EdtTestUtil.runInEdtAndWait(() -> {
        MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(document, fixture.getProject(), true);
        modelEx.addMarkupModelListener(fixture.getTestRootDisposable(), new MarkupModelListener() {
          @Override
          public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
            count.incrementAndGet();
          }

          @Override
          public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
            count.incrementAndGet();
          }
        });
      });

      fixture.type(' ');
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      assertEquals(0, count.get());
    });
  }

  @Test
  public void testTypeParametersMustNotBlinkWhenTypingInsideClass() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        package x;
        abstract class ToRun<TTTTTTTTTTTTTTT> implements Comparable<TTTTTTTTTTTTTTT> {
          private ToRun<TTTTTTTTTTTTTTT> delegate;
          <caret>
         \s
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      Document document = fixture.getDocument(fixture.getFile());
      EdtTestUtil.runInEdtAndWait(() -> {
        MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(document, fixture.getProject(), true);
        modelEx.addMarkupModelListener(fixture.getTestRootDisposable(), new MarkupModelListener() {
          @Override
          public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
            if (highlighter.getTextRange().substring(highlighter.getDocument().getText()).equals("TTTTTTTTTTTTTTT")) {
              throw new RuntimeException("Must not remove type parameter highlighter");
            }
          }
        });
      });

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      fixture.type("//xxx");
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      fixture.backspace();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      fixture.backspace();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      fixture.backspace();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      fixture.backspace();
      fixture.backspace();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
    });
  }

  @Test
  public void testLocallyUsedFieldHighlighting() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class A {
            String cons;
            void foo() {
                String local = null;
                <selection>cons</selection>.substring(1);    }
            public static void main(String[] args) {
                new A().foo();
            }}""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      fixture.enableInspectionTool(new UnusedDeclarationInspection(true));

      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).hasSize(1);
      assertEquals("Variable 'local' is never used", infos.getFirst().getDescription());

      fixture.type("local");

      infos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(infos).hasSize(1);
      assertEquals("Field 'cons' is never used", infos.getFirst().getDescription());
    });
  }

  @Test
  public void testOverrideMethodsHighlightingPersistWhenTypeInsideMethodBody() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
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
      fixture.configureByText(JavaFileType.INSTANCE, text);

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);
      fixture.type("//my comment inside method body, so class modifier won't be visited");
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);
    });
  }

  @Test
  public void testWhenTypingOverWrongReferenceIncludingRightAfterTheEndAndRightBeforeStartItsColorMustStayTheRedWithoutAnyBlinking() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class S {  int f() {
            return asfsdfsdfsd<caret>;
        }}""";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      HighlightInfo error = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      assertSame(HighlightInfoType.WRONG_REF, error.type);

      Document document = fixture.getDocument(fixture.getFile());
      Project project = fixture.getProject();
      Editor editor = fixture.getEditor();

      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.type("xxx");

        // right after typing, before the highlighting kicked in, its color must stay red
        for (HighlightInfo info : DaemonCodeAnalyzerImpl.getHighlights(document, HighlightInfoType.SYMBOL_TYPE_SEVERITY, project)) {
          if (TextRange.create(info).intersects(error)) {
            assertSame(HighlightInfoType.WRONG_REF, info.type);
            assertEquals("asfsdfsdfsd" + "xxx", info.getText());
          }
        }
      });

      EdtTestUtil.runInEdtAndWait(() -> {
        editor.getCaretModel().moveToOffset(error.startOffset);
        fixture.type("zzz");
        for (HighlightInfo info : DaemonCodeAnalyzerImpl.getHighlights(document, HighlightInfoType.SYMBOL_TYPE_SEVERITY, project)) {
          if (TextRange.create(info).intersects(error)) {
            assertSame(HighlightInfoType.WRONG_REF, info.type);
            assertEquals("zzz" + "asfsdfsdfsd" + "xxx", info.getText());
          }
        }
      });

      HighlightInfo error2 = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      assertSame(HighlightInfoType.WRONG_REF, error2.type);
    });
  }


  @Test
  public void testQuickFixRemainsAvailableAfterAnotherFixHasBeenAppliedInTheSameCodeBlockBefore() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.configureByFile(BASE_PATH + "QuickFixes.java");

      DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
      boolean old = settings.isNextErrorActionGoesToErrorsFirst();
      settings.setNextErrorActionGoesToErrorsFirst(true);

      try {
        Collection<HighlightInfo> errors = fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertThat(errors).hasSize(3);
        gotoNextErrorAndInvokeFix("Delete catch for 'java.io.IOException'");

        errors = fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertThat(errors).hasSize(2);

        gotoNextErrorAndInvokeFix("Delete catch for 'java.io.IOException'");

        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);

        gotoNextErrorAndInvokeFix("Delete catch for 'java.io.IOException'");

        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      }
      finally {
        settings.setNextErrorActionGoesToErrorsFirst(old);
      }
    });
  }

  /**
   * Moves the caret to the next error, and invokes the quick fix {@code text} there. Runs in one EDT block.
   */
  private void gotoNextErrorAndInvokeFix(@NotNull String text) {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    Project project = fixture.getProject();
    Editor editor = fixture.getEditor();
    PsiFile psiFile = fixture.getFile();
    EdtTestUtil.runInEdtAndWait(() -> {
      new GotoNextErrorHandler(true).invoke(project, editor, psiFile);

      List<IntentionAction> fixes = LightQuickFixTestCase.getAvailableActions(editor, psiFile);
      IntentionAction fix = assertContainsOneOf(fixes, text);

      WriteCommandAction.runWriteCommandAction(project, () -> fix.invoke(project, editor, psiFile));
    });
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
    assertNotNull(result, " object not found in collection " + collection);
    return result;
  }


  @Test
  public void testRangeHighlightersDoNotGetStuckForever() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class S { void ffffff() {fff<caret>fff();}}";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      Document document = fixture.getEditor().getDocument();
      MarkupModel markup = EdtTestUtil.runInEdtAndGet(() -> DocumentMarkupModel.forDocument(document, fixture.getProject(), true));
      TextRange[] highlightersBefore = read(() -> getHighlightersTextRange(markup));

      fixture.type("%%%%");
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();
      fixture.backspace();
      fixture.backspace();
      fixture.backspace();
      fixture.backspace();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      TextRange[] highlightersAfter = read(() -> getHighlightersTextRange(markup));

      assertThat(highlightersAfter).hasSize(highlightersBefore.length);
      for (int i = 0; i < highlightersBefore.length; i++) {
        TextRange before = highlightersBefore[i];
        TextRange after = highlightersAfter[i];
        assertEquals(before.getStartOffset(), after.getStartOffset());
        assertEquals(before.getEndOffset(), after.getEndOffset());
      }
    });
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

  @Test
  public void testModificationInsideCodeBlockDoesNotAffectErrorMarkersOutside() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class SSSSS {
            public static void suite() {
                <caret>
                new Runnable() {
                    public void run() {

                    }
                };
            }

        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      HighlightInfo error = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      assertEquals("'}' expected", error.getDescription());

      fixture.type("//comment");
      HighlightInfo error2 = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      assertEquals("'}' expected", error2.getDescription());
    });
  }

  @Test
  public void testErrorMarkerAtTheEndOfTheFile() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      EdtTestUtil.runInEdtAndWait(() -> {
        CommandProcessor.getInstance().executeCommand(project, () -> fixture.configureByFile(BASE_PATH + "ErrorMarkAtEnd.java"), "Cc", this);
        EditorTestUtil.setEditorVisibleSizeInPixels(fixture.getEditor(), 1000, 1000);
      });
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(project, () -> {
        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        while (offset < document.getTextLength()) {
          int i = StringUtil.indexOf(document.getText(), '}', offset, document.getTextLength());
          if (i == -1) break;
          editor.getCaretModel().moveToOffset(i);
          fixture.executeAction(IdeActions.ACTION_EDITOR_DELETE, editor);
        }
      }, "My", this));

      List<HighlightInfo> errs = fixture.waitHighlighting(HighlightSeverity.ERROR);
      assertThat(errs).hasSize(2);
      assertEquals("'}' expected", errs.getFirst().getDescription());

      fixture.undo();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
    });
  }

  // todo - StoreUtil.saveDocumentsAndProjectsAndApp cannot save in EDT. If it is called in EDT,
  //  in this case, task is done under a modal progress, so, no idea how to fix the test, except executing it not in EDT (as it should be)
  public void _testDaemonIgnoresFrameDeactivation() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    // return default value to avoid unnecessary save
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    @Language("JAVA")
    String text = "class S { ArrayList<caret>XXX x;}";
    fixture.configureByText(JavaFileType.INSTANCE, text);
    assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();

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

  @Test
  public void testApplyLocalQuickFix() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class X { static int sss; public int f() { return this.<caret>sss; }}";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      Editor editor = fixture.getEditor();
      PsiFile psiFile = fixture.getFile();
      EdtTestUtil.runInEdtAndWait(() -> ((EditorImpl)editor).getScrollPane().getViewport().setSize(1000, 1000));
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      assertThat(fixture.waitHighlighting(HighlightSeverity.WARNING)).hasSize(1);
      EdtTestUtil.runInEdtAndWait(() -> {
        List<HighlightInfo.IntentionActionDescriptor> actions = ShowIntentionsPass.getAvailableFixes(editor, psiFile, -1, ((EditorEx)editor).getExpectedCaretOffset());
        HighlightInfo.IntentionActionDescriptor descriptor = assertOneItem(actions);
        CodeInsightTestFixtureImpl.invokeIntention(descriptor.getAction(), psiFile, editor);
      });
      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertThat(EdtTestUtil.runInEdtAndGet(
        () -> ShowIntentionsPass.getAvailableFixes(editor, psiFile, -1, ((EditorEx)editor).getExpectedCaretOffset()))).isEmpty();
    });
  }


  @Test
  public void testApplyErrorInTheMiddle() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class <caret>X { " + """

            {
        //    String x = "<zzzzzzzzzz/>";
            }""".repeat(100) +
                    "\n}";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> ((EditorImpl)editor).getScrollPane().getViewport().setSize(1000, 1000));
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      fixture.type("//");
      List<HighlightInfo> errors = fixture.waitHighlighting(HighlightSeverity.ERROR);
      assertThat(errors).hasSize(2);

      fixture.backspace();
      fixture.backspace();

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
    });
  }


  @Test
  public void testErrorInTheEndOutsideVisibleArea() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("XML")
      String text = "<xml> \n" + StringUtil.repeatSymbol('\n', 1000) + "</xml>\nxxxxx<caret>";
      fixture.configureByText(XmlFileType.INSTANCE, text);

      Editor editor = fixture.getEditor();
      ProperTextRange visibleRange = EdtTestUtil.runInEdtAndGet(() -> makeEditorWindowVisible(new Point(0, 1000), editor));
      assertTrue(visibleRange.getStartOffset() > 0);

      HighlightInfo info = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      assertEquals("Top level element is not completed", info.getDescription());

      fixture.type("xxx");
      info = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      assertEquals("Top level element is not completed", info.getDescription());
    });
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


  @Test
  public void testEnterInCodeBlock() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class LQF {
            int wwwwwwwwwwww;
            public void main() {<caret>
                wwwwwwwwwwww = 1;
            }
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> ((EditorImpl)editor).getScrollPane().getViewport().setSize(1000, 1000));

      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
      assertThat(infos).hasSize(4);

      fixture.type('\n');
      infos = fixture.waitHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
      assertThat(infos).hasSize(4);

      fixture.deleteLine();

      infos = fixture.waitHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
      assertThat(infos).hasSize(4);
    });
  }


  @Test
  public void testTypingNearEmptyErrorElement() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class LQF {
            public void main() {
                int wwwwwwwwwwww = 1<caret>
            }
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> ((EditorImpl)editor).getScrollPane().getViewport().setSize(1000, 1000));

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);

      fixture.type(';');
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
    });
  }

  @Test
  public void testCancelsItSelfOnTypingInAlienProject(@TempDir Path tempDir) {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      final AtomicReference<CountDownLatch> started = new AtomicReference<>(new CountDownLatch(1));
      final AtomicReference<CountDownLatch> continued = new AtomicReference<>(new CountDownLatch(1));
      final AtomicBoolean isCanceledWhenContinued = new AtomicBoolean();
      class MyPass extends EditorBoundHighlightingPass {
        MyPass(@NotNull Editor editor, @NotNull PsiFile psiFile) { super(editor, psiFile, false); }

        @Override
        public void doCollectInformation(@NotNull ProgressIndicator progress) {
          try {
            started.get().countDown();
            while (!continued.get().await(10, TimeUnit.MILLISECONDS)) {
              ProgressManager.checkCanceled();
            }
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          finally {
            isCanceledWhenContinued.compareAndSet(false, progress.isCanceled());
          }
        }

        @Override
        public void doApplyInformationToEditor() {
        }
      }
      class Fac implements TextEditorHighlightingPassFactory {
        @Override
        public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
          return new MyPass(fixture.getEditor(), fixture.getFile());
        }
      }
      TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(fixture.getProject());
      continued.get().countDown(); // allow to continue
      Fac fac = new Fac();
      registrar.registerTextEditorHighlightingPass(fac, null, null, false, -1);
      String body = StringUtil.repeat("{String field = null;}\n", 10);
      @Language("JAVA")
      String text = "class X{ void f() {" + body + "<caret>\n} }";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      Path alienDir = Files.createDirectories(tempDir.resolve("alienProject"));
      Project alienProject = PlatformTestUtil.loadAndOpenProject(alienDir.resolve("alien.ipr"), fixture.getTestRootDisposable());

      AtomicBoolean typed = new AtomicBoolean();
      Path moduleFile = ProjectKt.getStateStore(alienProject).getProjectBasePath().resolve("x.iml");
      Module alienModule = WriteAction.computeAndWait(() -> ModuleManager.getInstance(alienProject).newModule(moduleFile, JavaModuleType.getModuleType().getId()));
      IndexingTestUtil.waitUntilIndexesAreReady(alienProject);
      VirtualFile alienRoot = HeavyTestHelper.createTestProjectStructure(alienModule, null, Files.createDirectories(tempDir.resolve("alienRoot")), true);
      EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(alienProject).commitAllDocuments());
      OpenFileDescriptor alienDescriptor = WriteAction.computeAndWait(() -> {
        VirtualFile alienFile = alienRoot.createChildData(this, "AlienFile.java");
        @Language("JAVA")
        String alienText = "class Alien { }";
        LoadTextUtil.write(null, alienFile, alienFile, alienText, -1);
        return new OpenFileDescriptor(alienProject, alienFile);
      });

      Editor alienEditor = EdtTestUtil.runInEdtAndGet(() -> {
        Editor editor = Objects.requireNonNull(FileEditorManager.getInstance(alienProject).openTextEditor(alienDescriptor, false));
        ((EditorImpl)editor).setCaretActive();
        return editor;
      });
      assertThat(fixture.waitHighlightingSurviveCancellations(HighlightSeverity.ERROR)).isEmpty();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      testDaemonCodeAnalyzer.waitForTermination();

      continued.set(new CountDownLatch(1));// do not allow to continue yet
      started.set(new CountDownLatch(1));
      isCanceledWhenContinued.set(false);
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      PsiFile psiFile = fixture.getFile();
      String testName = fixture.getTestName(false);
      EdtTestUtil.runInEdtAndWait(() -> {
        try {
          // restart daemon in the main project. should check for its cancel when typing in alien
          daemonCodeAnalyzer.restart(testName);

          Runnable callbackWhileWaiting = () -> {
            LOG.debug("callback called: typed="+typed+"; started:"+started+"; continued:"+continued+"; isCanceledWhenContinued:"+isCanceledWhenContinued);
            if (started.get().getCount() != 0) {
              return;  // wait until the pass is started
            }
            // and then type a character
            if (!typed.getAndSet(true)) {
              typeInAlienEditor(alienEditor, 'x');
              continued.get().countDown(); // allow to continue but check we should be canceled first
            }
          };
          // start daemon in the main project. should check for its cancel when typing in alien
          daemonCodeAnalyzer.restart(testName);
          testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile, callbackWhileWaiting);
        }
        catch (ProcessCanceledException _) {
        }
      });
      assertTrue(typed.get());

      assertEquals(0, continued.get().getCount());
      assertTrue(isCanceledWhenContinued.get());
    });
  }

  @Test
  public void testPasteInAnonymousCodeBlock() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X{ void f() {     int x=0;x++;
            Runnable r = new Runnable() { public void run() {
         <caret>
            }};
            <selection>int y = x;</selection>

        } }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> {
        PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_EDITOR_COPY);
        assertEquals("int y = x;", editor.getSelectionModel().getSelectedText());
        editor.getSelectionModel().removeSelection();
        PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_EDITOR_PASTE);
      });
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);
    });
  }

  @Test
  public void testPostHighlightingPassRunsOnEveryPsiModification() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String xText = "public class X { public static void ffffffffffffff(){} }";
      PsiFile x = fixture.createFile("X.java", xText);
      @Language("JAVA")
      String useText = """
        public class Use {
          { <caret>X.ffffffffffffff(); }
          public static void main(String[] args) { } // to avoid 'class Use never used'
        }""";
      PsiFile use = fixture.createFile("Use.java", useText);
      fixture.configureByExistingFile(use.getVirtualFile());

      enableDeadCodeInspection();

      Editor xEditor = createEditor(x.getVirtualFile());
      fixture.setActiveEditors(xEditor, fixture.getEditor()); // EditorTrackerImpl does not work correctly in case of multiple editors in tests
      PsiFile xPsiFile = read(() -> PsiDocumentManager.getInstance(fixture.getProject()).getPsiFile(xEditor.getDocument()));
      List<HighlightInfo> xInfos = waitHighlighting(xPsiFile, HighlightSeverity.WARNING);
      HighlightInfo info = ContainerUtil.find(xInfos, xInfo -> xInfo.getDescription().equals("Method 'ffffffffffffff()' is never used"));
      assertNull(info, xInfos.toString());

      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      List<HighlightInfo> useInfos = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(useInfos).isEmpty();

      fixture.type('/');
      fixture.type('/');

      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      xInfos = waitHighlighting(xPsiFile, HighlightSeverity.WARNING);
      info = ContainerUtil.find(xInfos, xInfo -> xInfo.getDescription().equals("Method 'ffffffffffffff()' is never used"));
      assertNotNull(info, xInfos.toString());
      Reference.reachabilityFence(xPsiFile);
    });
  }

  private void enableDeadCodeInspection() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(fixture.getProject()).getCurrentProfile();
    HighlightDisplayKey myDeadCodeKey = HighlightDisplayKey.findOrRegister(UnusedDeclarationInspectionBase.SHORT_NAME,
                                                                           UnusedDeclarationInspectionBase.getDisplayNameText(), UnusedDeclarationInspectionBase.SHORT_NAME);
    UnusedDeclarationInspectionBase myDeadCodeInspection = new UnusedDeclarationInspectionBase(true);
    fixture.enableInspectionTool(myDeadCodeInspection);
    PsiFile psiFile = fixture.getFile();
    assert read(() -> profile.isToolEnabled(myDeadCodeKey, psiFile));
  }

  @Test
  public void testErrorDisappearsRightAfterTypingInsideVisibleAreaWhileDaemonContinuesToChugAlong_Stress() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class X{\nint xxx;\n{\nint i = <selection>null</selection><caret>;\n" + StringUtil.repeat("{ this.hashCode(); }\n\n\n", 10000) + "}}";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      Editor editor = fixture.getEditor();
      Document document = editor.getDocument();
      EdtTestUtil.runInEdtAndWait(() -> {
        ((EditorImpl)editor).getScrollPane().getViewport().setSize(100, 100);
        DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        ((EditorImpl)editor).getScrollPane().getViewport().setViewPosition(new Point(0, 0));
        ((EditorImpl)editor).getScrollPane().getViewport().setExtentSize(new Dimension(100, 100000));
        ProperTextRange visibleRange = editor.calculateVisibleRange();
        assertTrue(visibleRange.getLength() > 0);
        assertTrue(visibleRange.getLength() < document.getTextLength());
      });

      HighlightInfo info = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      final String errorDescription = "Incompatible types. Found: 'null', required: 'int'";
      assertEquals(errorDescription, info.getDescription());

      AtomicBoolean errorRemoved = new AtomicBoolean();
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();

      EdtTestUtil.runInEdtAndWait(() -> {
        MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, fixture.getProject(), false);
        model.addMarkupModelListener(fixture.getTestRootDisposable(), new MarkupModelListener() {
          @Override
          public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
            HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
            if (info == null) return;
            String description = info.getDescription();
            if (errorDescription.equals(description)) {
              errorRemoved.set(true);

              List<ProgressableTextEditorHighlightingPass> passes = daemonCodeAnalyzer.getPassesToShowProgressFor(document);
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
      });
      fixture.type("1");

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertTrue(errorRemoved.get());
    });
  }

  @Test
  public void testDaemonWorksForDefaultProjectSinceItIsNeededInSettingsDialogForSomeReason() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      ProjectManager projectManager = ProjectManager.getInstance();
      assertNotNull(DaemonCodeAnalyzer.getInstance(projectManager.getDefaultProject()));
      EdtTestUtil.runInEdtAndWait(() -> {
        // dispose immediately to avoid interfering with further tests when the default project is disposed in write action
        ((ProjectManagerImpl)projectManager).disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      });
      assertFalse(((ProjectManagerImpl)projectManager).isDefaultProjectInitialized());
    });
  }

  @Test
  public void testChangeEventsAreNotAlwaysGeneric() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String body = """
        class X {
        <caret>    @org.PPP
            void dtoArrayDouble() {
            }
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, body);
      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> makeEditorWindowVisible(new Point(), editor));

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();

      fixture.type("//");
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      fixture.backspace();
      fixture.backspace();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();
    });
  }

  @Test
  public void testInterruptOnTyping_Stress() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @NonNls String filePath = "/psi/resolve/Thinlet.java";
      fixture.configureByFile(filePath);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      PsiFile psiFile = fixture.getFile();
      String testName = fixture.getTestName(false);
      AtomicBoolean thrown = new AtomicBoolean();
      EdtTestUtil.runInEdtAndWait(() -> {
        daemonCodeAnalyzer.restart(testName);
        try {
          Project project = psiFile.getProject();
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
          testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile, () -> fixture.type(' '));
        }
        catch (Exception ignored) {
          thrown.set(true);
        }
      });
      if (!thrown.get()) {
        fail("PCE must have been thrown");
      }
    });
  }

  @Test
  public void testTypingInsideCodeBlockDoesntLeadToCatastrophicUnusedEverything_Stress() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      InspectionProfileImpl profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
      profile.disableAllTools(project);
      @NonNls String filePath = "/psi/resolve/Thinlet.java";
      fixture.configureByFile(filePath);

      PsiFile psiFile = fixture.getFile();
      EdtTestUtil.runInEdtAndWait(() -> {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(psiFile.getProject());
      });
      List<HighlightInfo> errors = fixture.waitHighlighting(HighlightSeverity.ERROR);
      assertThat(errors).isEmpty();
      List<HighlightInfo> initialWarnings = fixture.waitHighlighting(HighlightSeverity.WARNING);
      assertThat(initialWarnings).isEmpty();
      int N_BLOCKS = read(() -> codeBlocks(psiFile).size());
      assertTrue(N_BLOCKS > 1000, "codeblocks :"+N_BLOCKS);
      Random random = new Random();
      int N = 10;
      Editor editor = fixture.getEditor();
      for (int i=0; i<N; i++) {
        EdtTestUtil.runInEdtAndWait(() -> {
          PsiCodeBlock block = codeBlocks(psiFile).get(random.nextInt(N_BLOCKS));
          editor.getCaretModel().moveToOffset(block.getLBrace().getTextOffset() + 1);
        });
        fixture.type("\n/*xxx*/");
        List<HighlightInfo> warnings = fixture.waitHighlighting(HighlightSeverity.WARNING);
        if (!warnings.isEmpty()) {
          System.out.println("\n-----\n"+read(() -> editor.getDocument().getText())+"\n--------\n");
        }
        assertThat(warnings).isEmpty();
      }
    });
  }

  @NotNull
  private static List<PsiCodeBlock> codeBlocks(@NotNull PsiFile psiFile) {
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

  @Test
  public void testCodeFoldingInSplittedWindowAppliesToAllEditors() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Set<Editor> applied = ConcurrentCollectionFactory.createConcurrentSet();
      Set<Editor> collected = ConcurrentCollectionFactory.createConcurrentSet();
      registerFakePass(applied, collected);

      fixture.configureByText(PlainTextFileType.INSTANCE, "");
      Editor editor1 = fixture.getEditor();
      Project project = fixture.getProject();
      PsiFile psiFile1 = fixture.getFile();
      FileEditor fileEditor2 = assertOneItem(EdtTestUtil.runInEdtAndGet(
        () -> FileEditorManager.getInstance(project).openFile(psiFile1.getVirtualFile())));
      Editor editor2 = ((TextEditor)fileEditor2).getEditor();
      Disposer.register(fixture.getTestRootDisposable(), () -> FileEditorManager.getInstance(project).closeFile(fileEditor2.getFile()));
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      String testName = fixture.getTestName(false);
      EdtTestUtil.runInEdtAndWait(() -> {
        daemonCodeAnalyzer.restart(testName);
        applied.clear();
        collected.clear();
        testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile1);
      });
      List<HighlightInfo> errors = getHighlights(editor1.getDocument(), null, project);
      assertThat(errors).isEmpty();

      assertEquals(collected, ContainerUtil.newHashSet(editor1, editor2));
      assertEquals(applied, ContainerUtil.newHashSet(editor1, editor2));
    });
  }

  // checks that only one instance is running
  public static class MySingletonAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    private static final AtomicBoolean wait = new AtomicBoolean();
    private static final AtomicReference<ProgressIndicator> running = new AtomicReference<>();
    private static final String SWEARING = "No swearing";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (!running.compareAndSet(null, ProgressIndicatorProvider.getGlobalProgressIndicator())) {
        throw new IllegalStateException("Already running: "+running);
      }
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        while (wait.get()) {
          Thread.onSpinWait();
        }
        holder.newAnnotation(HighlightSeverity.ERROR, SWEARING).range(element).create();
        iDidIt();
      }
      LOG.debug(getClass()+".annotate("+element+") = "+didIDoIt());
      running.set(null);
    }
  }

  @Test
  public void testTwoEditorsForTheSameDocumentDoNotCompeteForMarkupModelAndHighlightingDoesNotBlinkAfterModificationInSomeEditor() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
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
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertTrue(fixture.waitHighlighting(HighlightSeverity.ERROR).size() > 1000);// unresolved references
      Editor editor1 = fixture.getEditor();
      PsiFile psiFile1 = fixture.getFile();
      Project project = fixture.getProject();
      Editor editor2 = EdtTestUtil.runInEdtAndGet(() -> {
        Editor editor = EditorFactory.getInstance().createEditor(editor1.getDocument(), project);
        Disposer.register(fixture.getTestRootDisposable(), () -> EditorFactory.getInstance().releaseEditor(editor));
        TextEditor textEditor1 = new PsiAwareTextEditorProvider().getTextEditor(editor1);
        TextEditor textEditor2 = new PsiAwareTextEditorProvider().getTextEditor(editor);
        assertNotNull(textEditor1);
        assertNotNull(textEditor2);
        return editor;
      });
      fixture.setActiveEditors(editor1, editor2);
      MySingletonAnnotator.running.set(null);
      MySingletonAnnotator.wait.set(false);
      List<Future<?>> futures = new ArrayList<>();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      // check that 'MySingletonAnnotator' is run only once for two editors for the same document
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MySingletonAnnotator()}, ()-> {
        for (int i=0; i<10; i++) {
          EdtTestUtil.runInEdtAndWait(() -> {
            MySingletonAnnotator.wait.set(true);
            fixture.type("/");
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            futures.add(AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> MySingletonAnnotator.wait.set(false), 1000, TimeUnit.MILLISECONDS));
            testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile1);
          });

          // revert back
          EdtTestUtil.runInEdtAndWait(() -> {
            fixture.backspace();
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile1);
          });
        }
      });
      for (Future<?> future : futures) {
        future.cancel(false);
      }
    });
  }

  @Test
  public void testHighlightingInSplittedWindowFinishesEventually() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Collection<Editor> applied = ContainerUtil.createConcurrentList();
      Collection<Editor> collected = ContainerUtil.createConcurrentList();
      registerFakePass(applied, collected);

      @Language("JAVA")
      String text = "class X {" + "\n".repeat(1000) +
                    "}";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Editor editor1 = fixture.getEditor();
      Project project = fixture.getProject();
      Editor editor2 = EdtTestUtil.runInEdtAndGet(() -> {
        Editor editor = EditorFactory.getInstance().createEditor(editor1.getDocument(), project);
        Disposer.register(fixture.getTestRootDisposable(), () -> EditorFactory.getInstance().releaseEditor(editor));
        return editor;
      });
      fixture.setActiveEditors(editor1, editor2);

      waitForDaemonToFinish(fixture.getFile());

      assertThat(collected).containsOnly(editor1, editor2);
      assertThat(applied).containsOnly(editor1, editor2);

      applied.clear();
      collected.clear();
      fixture.setActiveEditors(editor1, editor2);
      fixture.type("/* xxx */");
      waitForDaemonToFinish(fixture.getFile());

      assertThat(collected).containsOnly(editor1, editor2);
      assertThat(applied).containsOnly(editor1, editor2);
    });
  }

  @Test
  @TestFor(issues = "IJPL-250174")
  public void testSharedHighlightingPassFinishesAfterItsEditorBecomesHidden() {
    daemonFixture.get().runTest(() -> checkSharedHighlightingPassWithHiddenEditor(true));
  }

  @Test
  @TestFor(issues = "IJPL-250174")
  public void testSharedHighlightingPassStaysFinishedWhenDuplicateRunsLater() {
    daemonFixture.get().runTest(() -> checkSharedHighlightingPassWithHiddenEditor(false));
  }

  private void checkSharedHighlightingPassWithHiddenEditor(boolean duplicateFirst) {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    Project project = fixture.getProject();
    var enabled = new AtomicBoolean();
    var owner = new AtomicReference<Editor>();
    var ownerStarted = new CountDownLatch(1);
    var ownerStopped = new CountDownLatch(1);
    var duplicateStopped = new CountDownLatch(1);
    var collected = new AtomicInteger();
    var applied = new AtomicInteger();
    var sharedPass = new AtomicReference<ProgressableTextEditorHighlightingPass>();
    class SharedPass extends ProgressableTextEditorHighlightingPass implements DumbAware {
      SharedPass(PsiFile file, Editor editor) {
        super(project, editor.getDocument(), "Test pass", file, editor,
              new TextRange(0, editor.getDocument().getTextLength()), false, null);
      }

      @Override
      protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
        collected.incrementAndGet();
        ownerStarted.countDown();
        if (duplicateFirst) {
          awaitSharedHighlightingPass(duplicateStopped);
        }
      }

      @Override
      protected void applyInformationWithProgress() {
        applied.incrementAndGet();
      }
    }
    TextEditorHighlightingPassRegistrar.getInstance(project).registerTextEditorHighlightingPass((file, editor) -> {
      if (!enabled.get()) return null;
      if (editor != owner.get()) {
        awaitSharedHighlightingPass(duplicateFirst ? ownerStarted : ownerStopped);
      }
      var pass = new SharedPass(file, editor);
      if (editor == owner.get()) {
        sharedPass.set(pass);
      }
      return pass;
    }, null, null, false, -1);
    fixture.configureByText(JavaFileType.INSTANCE, "class X {}");
    PsiFile psiFile = fixture.getFile();
    waitForDaemonToFinish(psiFile);
    var document = fixture.getEditor().getDocument();
    var provider = new PsiAwareTextEditorProvider();
    Editor[] editors = EdtTestUtil.runInEdtAndGet(() -> {
      var ownerEditor = EditorFactory.getInstance().createEditor(document, project);
      var duplicateEditor = EditorFactory.getInstance().createEditor(document, project);
      Disposer.register(fixture.getTestRootDisposable(), () -> EditorFactory.getInstance().releaseEditor(ownerEditor));
      Disposer.register(fixture.getTestRootDisposable(), () -> EditorFactory.getInstance().releaseEditor(duplicateEditor));
      return new Editor[]{ownerEditor, duplicateEditor};
    });
    var ownerEditor = editors[0];
    var duplicateEditor = editors[1];
    owner.set(ownerEditor);
    var ownerComponent = EdtTestUtil.runInEdtAndGet(() -> provider.getTextEditor(ownerEditor).getComponent());
    var duplicateComponent = EdtTestUtil.runInEdtAndGet(() -> provider.getTextEditor(duplicateEditor).getComponent());
    project.getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
      new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
          for (var fileEditor : fileEditors) {
            if (fileEditor instanceof TextEditor textEditor) {
              if (textEditor.getEditor() == ownerEditor) {
                ownerStopped.countDown();
              }
              else if (textEditor.getEditor() == duplicateEditor) {
                duplicateStopped.countDown();
              }
            }
          }
        }
      });
    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
    TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
    String testName = fixture.getTestName(false);
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        UITestUtil.runWithHeadlessProperty(false, () -> {
          ComponentUtil.forceMarkAsShowing(ownerComponent, false);
          ComponentUtil.forceMarkAsShowing(duplicateComponent, true);
          EditorTracker.getInstance(project).setActiveEditorsInTests(List.of(ownerEditor, duplicateEditor));
          enabled.set(true);
          daemonCodeAnalyzer.restart(testName);
          testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile);
          testDaemonCodeAnalyzer.waitForTermination();
          assertEquals(1, collected.get());
          assertEquals(1, applied.get());
          assertTrue(sharedPass.get().isFinished());
          assertTrue(DaemonCodeAnalyzerEx.isHighlightingCompleted(ownerEditor, project),
                     daemonCodeAnalyzer.getFileStatusMap().toString(document));
          assertTrue(DaemonCodeAnalyzerEx.isHighlightingCompleted(duplicateEditor, project));
        });
      }
      finally {
        enabled.set(false);
        ownerStarted.countDown();
        ownerStopped.countDown();
        duplicateStopped.countDown();
        ComponentUtil.forceMarkAsShowing(duplicateComponent, false);
      }
    });
  }

  private static void awaitSharedHighlightingPass(CountDownLatch latch) {
    try {
      assertTrue(latch.await(10, TimeUnit.SECONDS), "The highlighting pass must finish");
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
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
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(daemonFixture.get().getProject());
    registrar.registerTextEditorHighlightingPass(new Fac(), null, null, false, -1);
  }

  private volatile boolean runHeavyProcessing;
  @Test
  public void testDaemonDisablesItselfDuringHeavyProcessing() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      runHeavyProcessing = false;
      try {
        Set<Editor> applied = Collections.synchronizedSet(new HashSet<>());
        Set<Editor> collected = Collections.synchronizedSet(new HashSet<>());
        registerFakePass(applied, collected);

        fixture.configureByText(PlainTextFileType.INSTANCE, "");
        Editor editor = fixture.getEditor();
        Project project = fixture.getProject();
        DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
        EdtTestUtil.runInEdtAndWait(() -> {
          while (HeavyProcessLatch.INSTANCE.isRunning()) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          }
          fixture.type("xxx"); // restart daemon
          assertTrue(EditorTracker.Companion.getInstance(project).getActiveEditors().contains(editor));
          assertSame(editor, FileEditorManager.getInstance(project).getSelectedTextEditor());

          // wait for the first pass to complete
          long start = System.currentTimeMillis();
          while (daemonCodeAnalyzer.isRunning() || !applied.contains(editor)) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            if (System.currentTimeMillis() - start > 1000000) {
              fail("Too long waiting for daemon (" +(System.currentTimeMillis() - start)+"ms) ");
            }
          }
        });

        runHeavyProcessing = true;
        ApplicationManager.getApplication().executeOnPooledThread(() ->
          HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Processing, "my own heavy op", ()-> {
            while (runHeavyProcessing) {
              Thread.onSpinWait();
            }
          })
        );
        while (!HeavyProcessLatch.INSTANCE.isRunning()) {
          TimeoutUtil.sleep(1);
        }
        applied.clear();
        collected.clear();

        EdtTestUtil.runInEdtAndWait(() -> {
          fixture.type("xxx"); // try to restart daemon

          long start = System.currentTimeMillis();
          while (System.currentTimeMillis() < start + 5000) {
            assertThat(applied).isEmpty();  // it should not restart
            assertThat(collected).isEmpty();
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          }
        });
      }
      finally {
        runHeavyProcessing = false;
      }
    });
  }

  @Test
  public void testDaemonDoesNotDisableItselfDuringVFSRefresh() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      runHeavyProcessing = false;
      try {
        Set<Editor> applied = Collections.synchronizedSet(new HashSet<>());
        Set<Editor> collected = Collections.synchronizedSet(new HashSet<>());
        registerFakePass(applied, collected);

        fixture.configureByText(PlainTextFileType.INSTANCE, "");
        Editor editor = fixture.getEditor();
        Project project = fixture.getProject();
        DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
        EdtTestUtil.runInEdtAndWait(() -> {
          while (HeavyProcessLatch.INSTANCE.isRunning()) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          }
          fixture.type("xxx"); // restart daemon
          assertTrue(EditorTracker.Companion.getInstance(project).getActiveEditors().contains(editor));
          assertSame(editor, FileEditorManager.getInstance(project).getSelectedTextEditor());


          // wait for the first pass to complete
          long start = System.currentTimeMillis();
          while (daemonCodeAnalyzer.isRunning() || !applied.contains(editor)) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            if (System.currentTimeMillis() - start > 1000000) {
              fail("Too long waiting for daemon (" +(System.currentTimeMillis() - start)+"ms) ");
            }
          }
        });

        runHeavyProcessing = true;
        Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() ->
          HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Syncing, "my own vfs refresh", () -> {
            while (runHeavyProcessing) {
              Thread.onSpinWait();
            }
          })
        );
        while (!HeavyProcessLatch.INSTANCE.isRunning()) {
          TimeoutUtil.sleep(1);
        }
        applied.clear();
        collected.clear();

        fixture.type("xxx"); // try to restart daemon

        fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertThat(applied).isNotEmpty();  // it should restart
        assertThat(collected).isNotEmpty();
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

  @Test
  public void testDaemonMustDisableItselfDuringDocumentBulkModification() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      String testName = fixture.getTestName(false);

      Set<Editor> applied = Collections.synchronizedSet(new HashSet<>());
      Set<Editor> collected = Collections.synchronizedSet(new HashSet<>());
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.configureByText(PlainTextFileType.INSTANCE, "");
        Editor editor = fixture.getEditor();
        DocumentUtil.executeInBulk(editor.getDocument(), () -> {
          registerFakePass(applied, collected);

          assertTrue(EditorTracker.Companion.getInstance(project).getActiveEditors().contains(editor));
          assertSame(editor, FileEditorManager.getInstance(project).getSelectedTextEditor());

          applied.clear();
          collected.clear();

          daemonCodeAnalyzer.restart(testName); // try to restart daemon

          long start = System.currentTimeMillis();
          while (System.currentTimeMillis() < start + 5000) {
            assertThat(applied).isEmpty();  // it must not restart
            assertThat(collected).isEmpty();
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          }
        });
      });

      applied.clear();
      collected.clear();

      EdtTestUtil.runInEdtAndWait(() -> {
        daemonCodeAnalyzer.restart(testName); // try to restart daemon

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() < start + 5000 && applied.isEmpty()) {
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        }
      });
      assertThat(applied).isNotEmpty();  // it must restart outside bulk
      assertThat(collected).isNotEmpty();
    });
  }

  @Test
  public void testModificationInsideCodeBlockDoesNotRehighlightWholeFile() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
          int f = "error";
          int f() {
            return 11<caret>;
          }
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      HighlightInfo error = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      RangeHighlighterEx errorHighlighter = error.getHighlighter();
      assertEquals("Incompatible types. Found: 'java.lang.String', required: 'int'", error.getDescription());

      fixture.type("23");
      HighlightInfo after = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      assertSame(error, after);
      assertSame(errorHighlighter, after.getHighlighter());

      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> editor.getCaretModel().moveToOffset(0));
      fixture.type("/* */");
      HighlightInfo error2 = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
      assertEquals("Incompatible types. Found: 'java.lang.String', required: 'int'", error2.getDescription());
    });
  }

  @Test
  public void testCodeFoldingPassRestartsOnRegionUnfolding() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class Foo {
            void m() {

            }
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Editor editor = fixture.getEditor();
      PsiFile psiFile = fixture.getFile();
      EdtTestUtil.runInEdtAndWait(() -> EditorTestUtil.buildInitialFoldingsInBackground(editor));
      waitForDaemonToFinish(psiFile);
      EdtTestUtil.runInEdtAndWait(() -> EditorTestUtil.executeAction(editor, IdeActions.ACTION_COLLAPSE_ALL_REGIONS));
      waitForDaemonToFinish(psiFile);
      checkFoldingState("[FoldRegion +(25:33), placeholder='{}']");

      WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> editor.getDocument().insertString(0, "/*"));
      waitForDaemonToFinish(psiFile);
      checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion +(27:35), placeholder='{}']");

      EdtTestUtil.runInEdtAndWait(() -> EditorTestUtil.executeAction(editor, IdeActions.ACTION_EXPAND_ALL_REGIONS));
      waitForDaemonToFinish(psiFile);
      checkFoldingState("[FoldRegion -(0:37), placeholder='/.../', FoldRegion -(27:35), placeholder='{}']");
    });
  }

  @Test
  public void testChangingSettingsHasImmediateEffectOnOpenedEditor() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class C {\s
          void m() {
          }\s
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Editor editor = fixture.getEditor();
      PsiFile psiFile = fixture.getFile();
      EdtTestUtil.runInEdtAndWait(() -> EditorTestUtil.buildInitialFoldingsInBackground(editor));
      waitForDaemonToFinish(psiFile);
      checkFoldingState("[FoldRegion -(22:27), placeholder='{}']");

      JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
      boolean savedValue = settings.isCollapseMethods();
      try {
        settings.setCollapseMethods(true);
        EdtTestUtil.runInEdtAndWait(() -> CodeFoldingConfigurable.Util.applyCodeFoldingSettingsChanges());
        waitForDaemonToFinish(psiFile);
        checkFoldingState("[FoldRegion +(22:27), placeholder='{}']");
      }
      finally {
        settings.setCollapseMethods(savedValue);
      }
    });
  }

  private void checkFoldingState(String expected) {
    Editor editor = daemonFixture.get().getEditor();
    assertEquals(expected, EdtTestUtil.runInEdtAndGet(() -> Arrays.toString(editor.getFoldingModel().getAllFoldRegions())));
  }

  @Test
  public void testRehighlightInDebuggerExpressionFragment() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      PsiExpressionCodeFragment fragment = read(() -> JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("+ <caret>\"a\"", null,
                                      PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project)), true));
      fixture.configureByExistingFile(fragment.getVirtualFile());

      Editor editor = fixture.getEditor();
      ProperTextRange visibleRange = EdtTestUtil.runInEdtAndGet(() -> makeEditorWindowVisible(new Point(0, 0), editor));
      Document document = editor.getDocument();
      assertEquals(document.getTextLength(), visibleRange.getLength());

      try {
        EditorInfo editorInfo = new EditorInfo(document.getText());

        String newFileText = editorInfo.getNewFileText();
        EdtTestUtil.runInEdtAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
          if (!document.getText().equals(newFileText)) {
            document.setText(newFileText);
          }

          editorInfo.applyToEditor(editor);
        }));

        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));

        HighlightInfo error = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
        assertTrue(fixture.getTestDaemonCodeAnalyzer().isMarkedCodeFragment(document));

        assertEquals("Operator '+' cannot be applied to 'java.lang.String'", error.getDescription());

        fixture.type(" ");

        HighlightInfo after = assertOneItem(fixture.waitHighlighting(HighlightSeverity.ERROR));
        assertEquals("Operator '+' cannot be applied to 'java.lang.String'", after.getDescription());
      }
      finally {
        EdtTestUtil.runInEdtAndWait(() -> EditorFactory.getInstance().releaseEditor(editor));
      }
    });
  }

  @Test
  public void testDumbAwareHighlightingPassesStartEvenInDumbMode() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      testDaemonCodeAnalyzer.mustWaitForSmartModeByDefault(false);
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
        TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(project);
      DumbFac dumbFac = new DumbFac();
      registrar.registerTextEditorHighlightingPass(dumbFac, null, null, false, -1);
        class SmartFac implements TextEditorHighlightingPassFactory {
          @Override
          public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
            return new EditorBoundHighlightingPass(editor, psiFile, false) {
              @Override
              public void doCollectInformation(@NotNull ProgressIndicator progress) {
                if (DumbService.isDumb(project)) throw IndexNotReadyException.create();
                collected.add(SmartFac.this);
              }

              @Override
              public void doApplyInformationToEditor() {
                if (DumbService.isDumb(project)) return;
                applied.add(SmartFac.this);
              }
            };
          }
        }
      SmartFac smartFac = new SmartFac();
      registrar.registerTextEditorHighlightingPass(smartFac, null, null, false, -1);

      fixture.configureByText(PlainTextFileType.INSTANCE, "");
      PsiFile psiFile = fixture.getFile();
      EdtTestUtil.runInEdtAndWait(() -> testDaemonCodeAnalyzer.waitHighlightingSurviveCancellations(psiFile, HighlightSeverity.ERROR));
      assertThat(collected).containsOnly(dumbFac, smartFac);
      assertThat(applied).containsOnly(dumbFac, smartFac);
      collected.clear();
      applied.clear();

      CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, fixture.getTestRootDisposable());
      EdtTestUtil.runInEdtAndWait(() -> DumbModeTestUtils.runInDumbModeSynchronously(project, () -> {
        fixture.type(' ');
        testDaemonCodeAnalyzer.waitHighlightingSurviveCancellations(psiFile, HighlightSeverity.ERROR);

        TextEditorHighlightingPassFactory f = assertOneItem(collected);
        assertSame(dumbFac, f);
        TextEditorHighlightingPassFactory f2 = assertOneItem(applied);
        assertSame(dumbFac, f2);
      }));
    });
  }

  @Test
  public void testUncommittedByAccidentNonPhysicalDocumentMustNotHangDaemon() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String t = "class X { void f() { <caret> } }";
      fixture.configureByText(JavaFileType.INSTANCE, t);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed());

      Project project = fixture.getProject();
      VirtualFile tempDir = fixture.createTempVirtualDir();
      VirtualFile txtFile = WriteAction.computeAndWait(() -> tempDir.createChildData(this, "X.txt"));
      PsiFile originalPsiFile = read(() -> PsiManager.getInstance(project).findFile(txtFile));
      assertNotNull(originalPsiFile);
      assertTrue(read(() -> originalPsiFile.getViewProvider().supportsSendingPsiEvents()));

      PsiFile copyPsiFile = read(() -> (PsiFile)originalPsiFile.copy());
      assertFalse(read(() -> copyPsiFile.getViewProvider().supportsSendingPsiEvents()));

      Document documentCopy = read(() -> copyPsiFile.getViewProvider().getDocument());
      assertNotNull(documentCopy);
      String text = "class A{}";
      EdtTestUtil.runInEdtAndWait(() -> {
        documentCopy.setText(text);
        assertFalse(PsiDocumentManager.getInstance(project).isCommitted(documentCopy));
        assertTrue(PsiDocumentManager.getInstance(project).hasUncommitedDocuments());
      });

      fixture.type("String i=0;");
      LOG.debug("---");
      Editor editor = fixture.getEditor();
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        TestTimeOut timeOut = TestTimeOut.setTimeout(1, TimeUnit.MINUTES);
        CodeInsightContext editorContext = EditorContextManager.getEditorContext(editor, project);
        while (daemonCodeAnalyzer.isRunningOrPending()
               || PsiDocumentManager.getInstance(project).isUncommited(editor.getDocument())
               || !daemonCodeAnalyzer.getFileStatusMap().allDirtyScopesAreNullFor(editor.getDocument(), editorContext)) {
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          timeOut.assertNoTimeout("daemon");
        }

        assertThat(DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), HighlightSeverity.ERROR, project)).isNotEmpty();
        assertEquals(text, documentCopy.getText());  // retain non-phys document until after highlighting
        assertFalse(PsiDocumentManager.getInstance(project).isCommitted(documentCopy));
        assertTrue(PsiDocumentManager.getInstance(project).hasUncommitedDocuments());
      });
      Reference.reachabilityFence(originalPsiFile);
      Reference.reachabilityFence(copyPsiFile);
    });
  }

  @Test
  public void testPutArgumentsOnSeparateLinesIntentionMustNotRemoveErrorHighlighting() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class X{ static void foo(String s1, String s2, String s3) { foo(\"1\", 1.2, \"2\"<caret>); }}";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);

      Project project = fixture.getProject();
      Editor editor = fixture.getEditor();
      PsiFile psiFile = fixture.getFile();
      EdtTestUtil.runInEdtAndWait(() -> {
        List<IntentionAction> fixes = LightQuickFixTestCase.getAvailableActions(editor, psiFile);
        IntentionAction intention = assertContainsOneOf(fixes, "Put arguments on separate lines");
        assertNotNull(intention);

        WriteCommandAction.runWriteCommandAction(project, () -> intention.invoke(project, editor, psiFile));
      });
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).hasSize(1);
    });
  }


  @Test
  public void testHighlightingPassesAreInstantiatedOutsideEDTToImproveResponsiveness() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      AtomicReference<Throwable> violation = new AtomicReference<>();
      AtomicBoolean applied = new AtomicBoolean();
      class MyCheckingConstructorTraceFac implements TextEditorHighlightingPassFactory {
        @Override
        public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
          return new TestHighlightingPassesAreInstantiatedOutsideEDTToImproveResponsivenessPass(fixture.getProject());
        }

        final class TestHighlightingPassesAreInstantiatedOutsideEDTToImproveResponsivenessPass extends TextEditorHighlightingPass {
          private TestHighlightingPassesAreInstantiatedOutsideEDTToImproveResponsivenessPass(Project project) {
            super(project, fixture.getEditor().getDocument(), false);
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
      TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(fixture.getProject());
      registrar.registerTextEditorHighlightingPass(new MyCheckingConstructorTraceFac(), null, null, false, -1);
      @Language("JAVA")
      String text = "class C{}";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertTrue(applied.get());
      if (violation.get() != null) {
        throw violation.get();
      }
    });
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

  @Test
  public void testTextEditorHighlightingPassRegistrarMustNotAllowCyclesInPassDeclarationsOrCrazyPassIdsOmgMurphyLawStrikesAgain() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class C{}";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      TextEditorHighlightingPassRegistrarImpl registrar = (TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(fixture.getProject());
      int F2 = Pass.EXTERNAL_TOOLS;
      int forcedId1 = 256;
      int forcedId2 = 257;
      int forcedId3 = 258;
      assertThrowsIllegalArgument(() ->
        // afterCompletionOf and afterStartingOf must not intersect
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{F2}, new int[]{F2}, false, -1));
      assertThrowsIllegalArgument(() ->
        // afterCompletionOf and afterStartingOf must not contain forcedId
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId1));
      assertThrowsIllegalArgument(() ->
        // afterCompletionOf and afterStartingOf must not contain forcedId
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), null, new int[]{forcedId1}, false, forcedId1));
      assertThrowsIllegalArgument(() ->
        // afterCompletionOf and afterStartingOf must not contain crazy ids
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{0}, new int[]{F2}, false, forcedId1));
      assertThrowsIllegalArgument(() ->
        // afterCompletionOf and afterStartingOf must not contain crazy ids
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{-1}, new int[]{F2}, false, forcedId1));
      assertThrowsIllegalArgument(() ->
        // afterCompletionOf and afterStartingOf must not contain crazy ids
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{F2}, new int[]{0}, false, forcedId1));
      assertThrowsIllegalArgument(() ->
        // afterCompletionOf and afterStartingOf must not contain crazy ids
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{F2}, new int[]{-1}, false, forcedId1));
      assertThrowsIllegalArgument(() ->
        // afterCompletionOf and afterStartingOf must not contain crazy ids
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{32134}, new int[]{F2}, false, forcedId1));
      assertThrowsIllegalArgument(() ->
        // afterCompletionOf and afterStartingOf must not contain crazy ids
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{F2}, new int[]{982314}, false, forcedId1));

      assertThrowsIllegalArgument(() -> {
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId2}, null, false, forcedId1);
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId2);
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      });
      // non-direct cycle
      assertThrowsIllegalArgument(() -> {
        registrar.reRegisterFactories(); // clear caches from incorrect factories above
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId2}, null, false, forcedId1);
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId3}, null, false, forcedId2);
        registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId3);
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      });

      registrar.reRegisterFactories(); // clear caches from incorrect factories above
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), null, null, false, forcedId1);
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId1}, null, false, forcedId3);
      registrar.registerTextEditorHighlightingPass(new EmptyPassFactory(), new int[]{forcedId3}, null, false, forcedId2);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
    });
  }

  @Test
  public void testHighlightersMustDisappearWhenTheHighlightingIsSwitchedOff() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
          blah blah
          )(@*$)(*%@$)
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();
      PsiFile psiFile = fixture.getFile();
      EdtTestUtil.runInEdtAndWait(
        () -> HighlightingSettingsPerFile.getInstance(fixture.getProject()).setHighlightingSettingForRoot(psiFile, FileHighlightingSetting.SKIP_HIGHLIGHTING));

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
    });
  }

  @Test
  public void testTypingErrorElementMustHighlightIt() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class X { void f() { } }<caret>";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> makeEditorWindowVisible(new Point(0, 1000), editor));

      fixture.type("/");
      waitForDaemonToFinish(fixture.getFile());
      List<HighlightInfo> errors = getHighlights(editor.getDocument(), HighlightSeverity.ERROR, fixture.getProject());
      assertThat(errors).isNotEmpty();
      assertTrue(errors.toString().contains("'class' or 'interface' expected"));
    });
  }

  @Test
  public void testTypingInsideCodeBlockCanAffectUnusedDeclarationInTheOtherClass() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.enableInspectionTool(new UnusedSymbolLocalInspection());
      enableDeadCodeInspection();
      fixture.configureByFiles(null,
                               BASE_PATH+fixture.getTestName(true)+"/p2/A2222.java",
                               BASE_PATH+fixture.getTestName(true)+"/p1/A1111.java");
      assertEquals("A2222.java", fixture.getFile().getName());

      Project project = fixture.getProject();
      PsiFile mainFile = fixture.getFile();
      Document document1111 = read(() -> mainFile.getParent().findFile("A1111.java").getFileDocument());
      PsiFile psiFile1111 = read(() -> PsiDocumentManager.getInstance(project).getPsiFile(document1111));// avoid "cached psiFile is null, cancel all passes"
      Editor editor1111 = EdtTestUtil.runInEdtAndGet(
        () -> ((TextEditor)FileEditorManager.getInstance(project).getEditors(FileDocumentManager.getInstance().getFile(document1111))[0]).getEditor());
      Editor editor2222 = fixture.getEditor();
      fixture.setActiveEditors(editor2222, editor1111);
      PsiFile psiFile2222 = fixture.getFile();

      HighlightInfo info = assertOneItem(waitHighlighting(psiFile2222, HighlightSeverity.WARNING));
      assertEquals("Class 'A2222' is never used", info.getDescription());

      // uncomment (inside code block) the reference to A2222
      EdtTestUtil.runInEdtAndWait(() -> WriteCommandAction.writeCommandAction(project).run(
        ()->document1111.deleteString(document1111.getText().indexOf("//"), document1111.getText().indexOf("//")+2)));

      // now A2222 is no longer unused
      assertThat(waitHighlighting(psiFile2222, HighlightSeverity.WARNING)).isEmpty();
      Reference.reachabilityFence(psiFile1111);
    });
  }

  // test the other type of PSI change: child remove/child add
  @Test
  public void testTypingInsideCodeBlockCanAffectUnusedDeclarationInTheOtherClass2() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.enableInspectionTool(new UnusedSymbolLocalInspection());
      enableDeadCodeInspection();
      fixture.configureByFiles(null,
                               BASE_PATH+fixture.getTestName(true)+"/p1/A1111.java",
                               BASE_PATH+fixture.getTestName(true)+"/p2/A2222.java");
      assertEquals("A1111.java", fixture.getFile().getName());
      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> makeEditorWindowVisible(new Point(0, 1000), editor));
      Project project = fixture.getProject();
      PsiFile mainFile = fixture.getFile();
      Document document2222 = read(() -> mainFile.getParent().findFile("A2222.java").getFileDocument());
      PsiFile psiFile2222 = read(() -> PsiDocumentManager.getInstance(project).getPsiFile(document2222));// avoid "cached psiFile is null, cancel all passes"
      Editor editor2222 = EdtTestUtil.runInEdtAndGet(() -> ((TextEditor)FileEditorManager.getInstance(project)
        .getEditors(FileDocumentManager.getInstance().getFile(document2222))[0]).getEditor());
      fixture.setActiveEditors(editor, editor2222);
      LOG.debug("1==========================");
      HighlightInfo info = assertOneItem(fixture.waitHighlighting(HighlightSeverity.WARNING));
      assertEquals("Method 'foo()' is never used", info.getDescription());
      LOG.debug("2==========================");

      // uncomment (inside code block) the reference to A1111
      EdtTestUtil.runInEdtAndWait(() -> {
        WriteCommandAction.writeCommandAction(project).run(
          ()->document2222.deleteString(document2222.getText().indexOf("//"), document2222.getText().indexOf("//")+2));
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      });
      assertThat(waitHighlighting(psiFile2222, HighlightSeverity.ERROR)).isEmpty();
      // now foo() is no longer unused
      assertThat(fixture.waitHighlighting(HighlightSeverity.WARNING)).isEmpty();
      Reference.reachabilityFence(psiFile2222);
    });
  }

  @Test
  public void testTypingDoesNotLeaveInvalidPSIShitBehind() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
          void f() {
            ///
            int s;<caret>
            ///
          }
        }""";
      String bigText = text.replaceAll("///\n", "hashCode();\n".repeat(1000));
      fixture.configureByText(JavaFileType.INSTANCE, bigText);
      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> makeEditorWindowVisible(new Point(0, 1000), editor));
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      MarkupModel markupModel = EdtTestUtil.runInEdtAndGet(() -> DocumentMarkupModel.forDocument(editor.getDocument(), fixture.getProject(), true));
      for (int i=0; i<10; i++) {
        fixture.type(" // TS"+i);
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
        assertThat(read(() -> getErrorsFromMarkup(markupModel))).isEmpty();

        fixture.backspace();fixture.backspace();fixture.backspace();fixture.backspace();fixture.backspace();fixture.backspace();
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
        assertThat(read(() -> getErrorsFromMarkup(markupModel))).isEmpty();
      }
    });
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

  @Test
  public void testInvalidPSIElementsCreatedByTypingNearThemMustBeRemovedImmediatelyMeaningLongBeforeTheHighlightingPassFinished() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      //setTraceDaemonLoggerLevel();
      @Language("JAVA")
      String text = """
        class X {
          void f() {
            //XXX

            int s-<caret>; // ';' expected

          }
        }""";

      assertInvalidPSIElementHighlightingIsRemovedImmediatelyAfterRepairingChange(text, "';' expected", () -> fixture.backspace());
    });
  }

  @Test
  public void testInvalidPSIElementsCreatedByTypingNearThemMustBeRemovedImmediatelyMeaningLongBeforeTheHighlightingPassFinished2() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
          void f() {
            //XXX

            <caret> : // Unexpected token

          }
        }""";

      assertInvalidPSIElementHighlightingIsRemovedImmediatelyAfterRepairingChange(text, "Unexpected token", () -> fixture.type("//"));
    });
  }

  private void assertInvalidPSIElementHighlightingIsRemovedImmediatelyAfterRepairingChange(@Language("JAVA") String text,
                                                                                           String errorDescription,
                                                                                           Runnable repairingChange // the change which is supposed to fix the invalid PSI highlight
  ) {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    MyVerySlowAnnotator.wait.set(false);
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyVerySlowAnnotator()}, ()->{
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Editor editor = fixture.getEditor();
      Project project = fixture.getProject();
      PsiFile psiFile = fixture.getFile();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> makeEditorWindowVisible(new Point(0, 1000), editor));
      MarkupModel markupModel = EdtTestUtil.runInEdtAndGet(() -> DocumentMarkupModel.forDocument(editor.getDocument(), project, true));
      fixture.waitHighlighting(HighlightSeverity.ERROR);
      assertThat(read(() -> highlightsWithDescription(markupModel, errorDescription))).isNotEmpty();
      assertThat(read(() -> MyVerySlowAnnotator.myHighlights(markupModel))).isNotEmpty();

      project.getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
          ReadAction.run(()->
          LOG.debug("daemonStarting("+fileEditors+"). errors=\n"+StringUtil.join(DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), HighlightSeverity.ERROR, project),"\n")));
        }

        @Override
        public void daemonFinished(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
          ReadAction.run(()->
          LOG.debug("daemonFinished("+fileEditors+"). errors=\n"+StringUtil.join(DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), HighlightSeverity.ERROR, project),"\n")));
        }

        @Override
        public void daemonCancelEventOccurred(@NotNull String reason) {
          ReadAction.run(()->
          LOG.debug("daemonCancelEventOccurred("+reason+"). errors=\n"+StringUtil.join(DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), HighlightSeverity.ERROR, project),"\n")));
        }
      });
      AtomicBoolean success = new AtomicBoolean();
      EdtTestUtil.runInEdtAndWait(() -> {
        MyVerySlowAnnotator.wait.set(true);
        repairingChange.run(); //repair invalid psi
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
          testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile, checkHighlighted);
        }
        finally {
          MyVerySlowAnnotator.wait.set(false);
        }
      });

      assertThat(read(() -> highlightsWithDescription(markupModel, errorDescription))).isEmpty();
      assertThat(read(() -> MyVerySlowAnnotator.myHighlights(markupModel))).isNotEmpty();
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
  @Test
  public void testDaemonDoesRestartDuringMadMonkeyTyping/*Stress*/() {
    daemonFixture.get().runTest(() -> assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(MANY_LAMBDAS_INITIAL, MANY_LAMBDAS_TEXT_TO_TYPE, null));
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
  @Test
  public void testDaemonDoesRestartDuringMadMonkeyTyping2/*Stress*/() {
    daemonFixture.get().runTest(() -> assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(LONG_LINE_WITH_PARENS_INITIAL_TEXT, LONG_LINE_WITH_PARENS_TEXT_TO_TYPE, null));
  }

  @Test
  public void testDaemonDoesNotLeaveObsoleteErrorElementHighlightsBehind/*Stress*/() {
    Random random = new Random();
    daemonFixture.get().runTest(() -> assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(MANY_LAMBDAS_INITIAL, MANY_LAMBDAS_TEXT_TO_TYPE, () -> TimeoutUtil.sleep(random.nextInt(10))));
  }
  @Test
  public void testDaemonDoesNotLeaveObsoleteErrorElementHighlightsBehind2/*Stress*/() {
    Random random = new Random();
    daemonFixture.get().runTest(() -> assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(LONG_LINE_WITH_PARENS_INITIAL_TEXT, LONG_LINE_WITH_PARENS_TEXT_TO_TYPE, () -> TimeoutUtil.sleep(random.nextInt(10))));
  }

  // start typing in the empty java file char by char
  // after each typing, wait for the daemon to start and immediately proceed to type the next char
  // thus making daemon interrupt itself constantly, in hope for multiple highlighting sessions overlappings to manifest themselves more quickly.
  // after all typings are over, wait for final highlighting to complete and check that no errors are left in the markup
  private void assertDaemonRestartsAndLeavesNoErrorElementsInTheEnd(String initialText, String textToType, Runnable afterWaitForDaemonToStart) {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    Project project = fixture.getProject();
    // run expensive consistency checks on each typing
    HighlightInfoUpdaterImpl updater = (HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(project);
    updater.runAssertingInvariants(() -> {
      String finalText = initialText.replace("<caret>", textToType);
      fixture.configureByText(JavaFileType.INSTANCE, finalText);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      Editor editor = fixture.getEditor();
      PsiFile psiFile = fixture.getFile();
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      for (int i=0; i<10; i++) {
        //System.out.println("i = " + i);
        PassExecutorService.LOG.debug("i = " + i);
        WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().setText("  "));
        fixture.waitHighlighting(HighlightSeverity.ERROR); // reset various optimizations e.g. FileStatusMap.getCompositeDocumentDirtyRange
        MarkupModel markupModel = EdtTestUtil.runInEdtAndGet(() -> DocumentMarkupModel.forDocument(editor.getDocument(), project, true));
        EdtTestUtil.runInEdtAndWait(() -> {
          for (int c = 0; c < finalText.length(); c++) {
            PassExecutorService.LOG.debug("c = " + c);
            //System.out.println("  c = " + c);
            int o=c;
            //updater.assertNoDuplicates(myFile, getErrorsFromMarkup(markupModel), "errors from markup ");
            WriteCommandAction.runWriteCommandAction(project, () -> {
              assertFalse(daemonCodeAnalyzer.isRunning());
              long docStamp = editor.getDocument().getModificationStamp();
              char charToType = finalText.charAt(o);
              fixture.type(charToType);
              if (docStamp != editor.getDocument().getModificationStamp()) { // condition could be false when type handler does overtype ')' with already existing ')'
                assertFalse(daemonCodeAnalyzer.isAllAnalysisFinished(psiFile));
              }
            });
            //updater.assertNoDuplicates(myFile, getErrorsFromMarkup(markupModel), "errors from markup ");
            daemonCodeAnalyzer.restart(psiFile, this);
            List<HighlightInfo> errorsFromMarkup = getErrorsFromMarkup(markupModel);
            //updater.assertNoDuplicates(myFile, errorsFromMarkup, "errors from markup ");
            //((HighlightInfoUpdaterImpl)HighlightInfoUpdater.getInstance(getProject())).assertMarkupDataConsistent(myFile);
            PassExecutorService.LOG.debug(" errorsfrommarkup:\n" + StringUtil.join(ContainerUtil.sorted(errorsFromMarkup, Segment.BY_START_OFFSET_THEN_END_OFFSET), "\n") + "\n-----\n");
            testDaemonCodeAnalyzer.waitForDaemonToStart(psiFile, 30 * 1000);
            if (afterWaitForDaemonToStart != null) {
              afterWaitForDaemonToStart.run();
            }
          }
        });
        EdtTestUtil.runInEdtAndWait(() -> {
          // some chars might be inserted by TypeHandlers
          while (!editor.getDocument().getText().substring(editor.getCaretModel().getOffset()).isEmpty()) {
            fixture.executeAction(IdeActions.ACTION_EDITOR_DELETE, editor);
          }
          LOG.debug("All typing completed. " +
                    "\neditor text:-----------\n"+editor.getDocument().getText()+"\n-------\n"+
                    "errors in markup: " + StringUtil.join(getErrorsFromMarkup(markupModel), "\n") + "\n-----\n");
          testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile);
          assertThat(getErrorsFromMarkup(markupModel)).as(editor.getDocument().getText()).isEmpty();
        });
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

  @Test
  public void testMultiplePSIInvalidationsMustDelayTheirHighlightersRemovalForShortTimeToAvoidFlickering() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
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
        fixture.configureByText(JavaFileType.INSTANCE, text);
        Editor editor = fixture.getEditor();
        Project project = fixture.getProject();
        PsiFile psiFile = fixture.getFile();
        EdtTestUtil.runInEdtAndWait(() -> makeEditorWindowVisible(new Point(0, 1000), editor));

        MarkupModelEx markupModel = EdtTestUtil.runInEdtAndGet(() -> (MarkupModelEx)DocumentMarkupModel.forDocument(editor.getDocument(), project, true));
        fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertThat(read(() -> MyXXXIdentifierAnnotator.myHighlights(markupModel))).hasSize(5);

        List<String> events = Collections.synchronizedList(new ArrayList<>());
        markupModel.addMarkupModelListener(fixture.getTestRootDisposable(), new MarkupModelListener() {
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
        List<PsiIdentifier> identifiers = read(() -> {
          List<PsiIdentifier> result = new ArrayList<>();
          psiFile.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitIdentifier(@NotNull PsiIdentifier identifier) {
              super.visitIdentifier(identifier);
              if (identifier.getText().equals("xxx")) {
                result.add(identifier);
              }
            }
          });
          return result;
        });
        EdtTestUtil.runInEdtAndWait(() -> {
          for (PsiIdentifier identifier : identifiers) {
            WriteCommandAction.writeCommandAction(project).run(() -> identifier.replace(PsiElementFactory.getInstance(project).createIdentifier("xxx")));
            assertFalse(identifier.isValid());
          }
        });
        fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertThat(events).isEmpty();
      });
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

  @Test
  public void testDaemonRestartsEvenWhenCanceledDuringRunUpdateMethodCallIsRunning() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass<caret> {

          }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      Project project = fixture.getProject();
      PsiFile psiFile = fixture.getFile();
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      String testName = fixture.getTestName(false);
      // warmup highlighting first, calibrating before that would make little sense
      for (int i = 0; i < 10; i++) {
        EdtTestUtil.runInEdtAndWait(() -> {
          fixture.type("x");
          testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile);
          fixture.backspace();
          PsiDocumentManager.getInstance(project).commitAllDocuments();
        });
      }
      long avgElapsedTime = 0;
      int CALIBRATE_N = 100;
      // compute time the highlighting takes to highlight this file completely
      for (int i = 0; i < CALIBRATE_N; i++) {
        avgElapsedTime += EdtTestUtil.runInEdtAndGet(() -> {
          fixture.type("x");
          long elapsed = TimeoutUtil.measureExecutionTime(() -> testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile));
          fixture.backspace();
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          return elapsed;
        });
      }

      avgElapsedTime /= CALIBRATE_N; // compute avg time the daemon takes to highlight this sample. then we use that time to delay in hope that DAI.runUpdate() is about to run
      LOG.debug("avgElapsedTime = " + avgElapsedTime);

      Random random = new Random();
      long maxDelay = avgElapsedTime;
      for (int i = 0; i < 1000; i++) {
        LOG.debug("i = " + i);
        EdtTestUtil.runInEdtAndWait(() -> {
          fixture.type("x");
          long delay = random.nextLong(maxDelay+1);
          Future<?> future = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            daemonCodeAnalyzer.restart(testName);
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

          testDaemonCodeAnalyzer.waitForDaemonToStart(psiFile, 60_000);
          fixture.backspace();
          PsiDocumentManager.getInstance(project).commitAllDocuments();
        });
      }
    });
  }

  @Test
  @TestFor(issues = "IJPL-244762")
  public void testModalityChangesRespectNestedDaemonSuspensions() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      var first = Disposer.newDisposable(fixture.getTestRootDisposable());
      var second = Disposer.newDisposable(fixture.getTestRootDisposable());
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      assertTrue(daemonCodeAnalyzer.isUpdateByTimerEnabled());
      EdtTestUtil.runInEdtAndWait(() -> {
        daemonCodeAnalyzer.disableUpdateByTimer(first);
        daemonCodeAnalyzer.disableUpdateByTimer(second);
        try {
          var modalEntity = new Object();
          LaterInvocator.enterModal(modalEntity);
          try {
            assertFalse(daemonCodeAnalyzer.isUpdateByTimerEnabled());
          }
          finally {
            LaterInvocator.leaveModal(modalEntity);
          }
          assertFalse(daemonCodeAnalyzer.isUpdateByTimerEnabled(), "Closing a modal dialog must preserve the daemon suspensions");
          Disposer.dispose(first);
          assertFalse(daemonCodeAnalyzer.isUpdateByTimerEnabled(), "The remaining suspension must keep the daemon disabled");
        }
        finally {
          Disposer.dispose(first);
          Disposer.dispose(second);
        }
      });
      assertTrue(daemonCodeAnalyzer.isUpdateByTimerEnabled(), "The daemon must resume after the last suspension ends");
    });
  }

  enum DEvent { STARTED, FINISHED, CANCELED }
  @Test
  public void testDaemonListenerEventsMustBePairedEvenWhenModalitySuddenlyChangedHalfRoad() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass<caret> {

          }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      List<Pair<DEvent,String>> eventLog = Collections.synchronizedList(new ArrayList<>());
      fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
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
      PsiFile psiFile = fixture.getFile();
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      for (int i=0; i<100; i++) {
        EdtTestUtil.runInEdtAndWait(() -> {
          testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile);
          eventLog.clear();
          Disposable disposable = Disposer.newDisposable();
          fixture.type("x");
          testDaemonCodeAnalyzer.waitForDaemonToStart(psiFile, 10_000);
          daemonCodeAnalyzer.disableUpdateByTimer(disposable);
          try {
            {
              long deadline = System.currentTimeMillis() + 10; // do something for awhile
              while (System.currentTimeMillis() < deadline) {
                PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
              }
            }

            fixture.type("y");
          }
          finally {
            Disposer.dispose(disposable); //reenable DCA
          }
          testDaemonCodeAnalyzer.waitForDaemonToFinish(psiFile);

          assertEventsArePaired(eventLog);
          fixture.backspace();
          fixture.backspace();
        });
      }
    });
  }

  private static void assertEventsArePaired(@NotNull List<? extends Pair<DEvent, String>> log) {
    String toString = log.toString();
    int starts = 0;
    int ends = 0;
    for (Pair<DEvent,String> e : log) {
      switch(e.getFirst()) {
        case STARTED:
          assertEquals(ends, starts, toString);
          starts++;
          break;
        case CANCELED:
        case FINISHED:
          assertTrue(starts > 0, toString);
          assertTrue(starts >= ends, toString);
          ends++;
          break;
        default:
          fail(toString);
      }
    }
    assertEquals(starts, ends, toString);
  }

  @Test
  public void testFileOutsideProjectRootsMustNotRestartDaemonTooOften() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      VirtualFile root = fixture.createTempVirtualDir();
      VirtualFile virtualFile = WriteAction.computeAndWait(() -> root.createChildDirectory(root, ".git").createChildData(root, "config"));

      String text = "[xxx]\nblah-blah";
      WriteAction.runAndWait(() -> LoadTextUtil.write(null, virtualFile, virtualFile, text, -1));

      assertEquals(PlainTextFileType.INSTANCE, virtualFile.getFileType());
      Project project = fixture.getProject();
      PsiFile psiFile = read(() -> PsiManager.getInstance(project).findFile(virtualFile));
      assertNull(psiFile, String.valueOf(psiFile));

      // Watch the PSI check because the daemon can loop before a highlighting session starts.
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      Future<?> previousRenewal = getFileRenewalTask(daemonCodeAnalyzer);
      fixture.configureByExistingFile(virtualFile);

      EdtTestUtil.runInEdtAndWait(() -> {
        PlatformTestUtil.waitWithEventsDispatching("The daemon must check the file for PSI",
                                                  () -> getFileRenewalTask(daemonCodeAnalyzer) != previousRenewal, 10);
        Future<?> renewal = getFileRenewalTask(daemonCodeAnalyzer);
        PlatformTestUtil.waitWithEventsDispatching("The daemon must finish checking the file for PSI", renewal::isDone, 10);
        renewal.get();
        // Run any retry that the daemon posted to the EDT before comparing the futures.
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertSame(renewal, getFileRenewalTask(daemonCodeAnalyzer), "The daemon scheduled another check for a file without PSI");
      });
    });
  }


  @Test
  public void testStartingTypingCommentMustExtendExistingHighlighterButShrinkItBackAfterRehighlighting() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
           void foo() {
             Str/*ing xxx;
           }
        }""";

      fixture.configureByText(JavaFileType.INSTANCE, text);
      List<HighlightInfo> errs = fixture.waitHighlighting(HighlightSeverity.ERROR);
      HighlightInfo info = ContainerUtil.find(errs, e -> "Cannot resolve symbol 'Str'".equals(e.getDescription()));
      assertTrue(info != null && info.getText().equals("Str"), errs.toString());

      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> {
        int i = editor.getDocument().getText().indexOf("/*");
        WriteCommandAction.writeCommandAction(fixture.getProject()).run(() -> editor.getDocument().insertString(i, "/*"));
      });
      errs = fixture.waitHighlighting(HighlightSeverity.ERROR);
      info = ContainerUtil.find(errs, e -> "Cannot resolve symbol 'Str'".equals(e.getDescription()));
      assertTrue(info != null && info.getText().equals("Str"), errs.toString());
    });
  }

  @Test
  public void testGarbageCollectedFileViewProviderAtUnfortunateMomentMustNotLeadToDanglingRangeHighlighters() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        class X {
           void foo() {
             String xxx;
           }
        }""";

      fixture.configureByText(JavaFileType.INSTANCE, text);
      fixture.waitHighlighting(HighlightSeverity.ERROR);
      String h1 = read(() -> renderHighlighters(getSortedHighlights()));

      GCWatcher tracking = GCWatcher.tracking(fixture.getFile());
      fixture.setFile(null);
      Project project = fixture.getProject();
      EdtTestUtil.runInEdtAndWait(() -> {
        IntentionsUI.getInstance(project).invalidate(); // clear com.intellij.codeInsight.intention.impl.CachedIntentions.myProject
        EditorNotificationsImpl editorNotifications = (EditorNotificationsImpl)EditorNotifications.getInstance(project);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        editorNotifications.completeAsyncTasks(); // EditorNotificationsImpl might retain psiFile in its update queue
        tracking.ensureCollectedWithinTimeout(30_000);
      });

      Editor editor = fixture.getEditor();
      fixture.setFile(read(() -> PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument())));
      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      fixture.waitHighlightingSurviveCancellations(HighlightSeverity.ERROR); // highlighting may be canceled due to PSI elements eviction
      String h2 = read(() -> renderHighlighters(getSortedHighlights()));

      assertEquals(h1, h2);
    });
  }

  static @NotNull String renderHighlighters(List<? extends RangeHighlighter> highlighters) {
    return StringUtil.join(highlighters, h->h.getTextRange()+":"+h.getLayer()+":"+h.getTextAttributes(EditorColorsUtil.getGlobalOrDefaultColorScheme()), "\n");
  }

  private List<RangeHighlighter> getSortedHighlights() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    List<RangeHighlighter> highlighters = getAllValidHighlighters(DocumentMarkupModel.forDocument(fixture.getEditor().getDocument(), fixture.getProject(), true));
    return ContainerUtil.sorted(highlighters, (o1, o2) -> {
      HighlightInfo h1 = HighlightInfo.fromRangeHighlighter(o1);
      HighlightInfo h2 = HighlightInfo.fromRangeHighlighter(o2);
      return h1 == null || h2 == null ? Segment.BY_START_OFFSET_THEN_END_OFFSET.compare(o1, o2) : HighlightInfoUpdaterImpl.BY_OFFSETS_AND_HASH_ERRORS_FIRST.compare(h1, h2);
    });
  }

  @Test
  public void testLaunchingZillionCoroutinesDoesNotSaturateDefaultPoolToThePointOfFrozenHighlighting() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      CountDownLatch latch = new CountDownLatch(1);
      List<CompletableFuture<Unit>> jobs;
      List<HighlightInfo> errs;
      try {
        @Language("JAVA")
        String text = "class X {  void foo() {\n" +
                      "     String xxx;\n".repeat(1000) +
                      "}}";

        fixture.configureByText(JavaFileType.INSTANCE, text); // first give a chance an indexer to complete indexing
        Project project = fixture.getProject();
        TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
        EdtTestUtil.runInEdtAndWait(() -> {
          PsiDocumentManager.getInstance(project).commitAllDocuments(); // give chance daemon to queue status update on doc commit
          testDaemonCodeAnalyzer.waitForUpdateFileStatusBackgroundQueueInTests(); // it uses Alarm which uses coroutines, so we'll give it a chance
        });
        CoroutineScope scope = ((ComponentManagerEx)project).getCoroutineScope();
        jobs = IntStream.range(0, 200)
          .mapToObj(_ -> BuildersKt.launch(scope, scope.getCoroutineContext(), CoroutineStart.DEFAULT, (_, _) -> {
            try {
              latch.await(1, TimeUnit.MINUTES);
            }
            catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            return Unit.INSTANCE;
          }))
          .map(job -> FutureKt.asCompletableFuture(job))
          .toList();
        errs = fixture.waitHighlighting(HighlightSeverity.ERROR);
      }
      finally {
        latch.countDown();
      }
      assertTrue(errs.size()>900, errs.toString());
      ConcurrencyUtil.getAll(jobs);
    });
  }
}
