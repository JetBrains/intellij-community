// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.CanChangeDocumentDuringHighlighting;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.VisibleHighlightingPassFactory;
import com.intellij.codeInsight.multiverse.CodeInsightContextUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.configurationStore.StoreUtilKt;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.utils.ActionsOnSaveTestUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCWatcher;
import kotlin.Unit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesFixture.daemonRespondToChangesFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * tests general daemon behaviour/interruptibility/restart during highlighting
 */
@SkipSlowTestLocally
@CanChangeDocumentDuringHighlighting
@TestApplication
public class FileStatusMapTest {
  private static final Logger LOG = Logger.getInstance(FileStatusMapTest.class);
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typing/";

  private final TestFixture<DaemonRespondToChangesFixture> daemonFixture =
    daemonRespondToChangesFixture(() -> LocalInspectionTool.EMPTY_ARRAY, null, LanguageLevel.JDK_11);

  @BeforeEach
  void setUp() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.enableInspectionTool(new UnusedDeclarationInspection());
    UndoManager.getInstance(fixture.getProject());
  }

  @Test
  public void testHighlightersUpdate() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.configureByFile(BASE_PATH + "HighlightersUpdate.java");
      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Document document = fixture.getDocument(file);
      FileStatusMap fileStatusMap = fixture.getDaemonCodeAnalyzer().getFileStatusMap();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();
      List<HighlightInfo> errors = ReadAction.computeBlocking(() -> DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.ERROR, project));
      assertThat(errors).hasSize(1);
      TextRange dirty = ReadAction.computeBlocking(() -> fileStatusMap.getFileDirtyScope(document, file, Pass.UPDATE_ALL));
      assertNull(dirty);

      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.type(' ');
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        assertNotNull(fileStatusMap.getFileDirtyScope(document, file, Pass.UPDATE_ALL));
      });
    });
  }


  @Test
  public void testNoPsiEventsAltogether() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.configureByFile(BASE_PATH + "HighlightersUpdate.java");
      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Document document = fixture.getDocument(file);
      FileStatusMap fileStatusMap = fixture.getDaemonCodeAnalyzer().getFileStatusMap();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.type(' ');
        fixture.backspace();
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        TextRange dirty = fileStatusMap.getFileDirtyScope(document, file, Pass.UPDATE_ALL);
        assertEquals(file.getTextRange(), dirty); // have to rehighlight whole file in case no PSI events have come
      });
    });
  }

  @Test
  public void testRenameClass() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass<caret> {
        
          }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Document document = fixture.getDocument(file);
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      EdtTestUtil.runInEdtAndWait(() -> {
        PsiClass psiClass = ((PsiJavaFile)file).getClasses()[0];
        new RenameProcessor(project, psiClass, "Class2", false, false).run();
        testDaemon.waitForUpdateFileStatusBackgroundQueueInTests();
        TextRange dirty = daemon.getFileStatusMap().getFileDirtyScope(document, file, Pass.UPDATE_ALL);
        assertEquals(file.getTextRange(), dirty);
      });

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertTrue(EdtTestUtil.runInEdtAndGet(() -> daemon.isErrorAnalyzingFinished(file)));
    });
  }

  @Test
  public void testTypingSpace() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
          class AClass <caret> {
        
          }
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Editor editor = fixture.getEditor();
      Document document = fixture.getDocument(file);
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.type("  ");
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        PsiElement elementAtCaret = file.findElementAt(editor.getCaretModel().getOffset());
        assertTrue(elementAtCaret instanceof PsiWhiteSpace);
        testDaemon.waitForUpdateFileStatusBackgroundQueueInTests();
        TextRange dirty = daemon.getFileStatusMap().getFileDirtyScope(document, file, Pass.UPDATE_ALL);
        assertEquals(elementAtCaret.getTextRange(), dirty);
      });
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertTrue(EdtTestUtil.runInEdtAndGet(() -> daemon.isErrorAnalyzingFinished(file)));
    });
  }

  @Test
  public void testFileStatusMapDirtyPSICachingWorks() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class <caret>S { int ffffff =  0;}";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

      List<String> passCreation = ContainerUtil.createLockFreeCopyOnWriteList();
      Project project = fixture.getProject();
      Editor editor = fixture.getEditor();
      class Fac implements TextEditorHighlightingPassFactory {
        @Override
        public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
          TextRange textRange = FileStatusMap.getDirtyTextRange(editor.getDocument(), psiFile, Pass.UPDATE_ALL);
          if (textRange == null) return null;
          return new TestFileStatusMapDirtyCachingWorksPass(project);
        }

        final class TestFileStatusMapDirtyCachingWorksPass extends TextEditorHighlightingPass {
          private TestFileStatusMapDirtyCachingWorksPass(Project project) {
            super(project, editor.getDocument(), false);
            passCreation.add(ExceptionUtil.getThrowableText(new Throwable()));
            LOG.debug("pass creation");
          }

          @Override
          public void doCollectInformation(@NotNull ProgressIndicator progress) {
          }

          @Override
          public void doApplyInformationToEditor() {
          }
        }
      }
      TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(project);
      registrar.registerTextEditorHighlightingPass(new Fac(), null, null, false, -1);
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      String testName = fixture.getTestName(false);
      EdtTestUtil.runInEdtAndWait(() -> {
        daemon.restart(testName);
        passCreation.clear();
      });
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertThat(passCreation).hasSize(1);

      //cached
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertThat(passCreation).hasSize(1);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertThat(passCreation).hasSize(1);

      fixture.type(' ');
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertEquals(2, passCreation.size(), passCreation.toString());
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertEquals(2, passCreation.size(), passCreation.toString());
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      assertEquals(2, passCreation.size(), passCreation.toString());
    });
  }

  @Test
  public void testFileStatusMapDirtyDocumentRangeWorks() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class <caret>S { int ffffff =  0;}";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
      fixture.waitHighlighting(HighlightSeverity.ERROR);
      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Editor editor = fixture.getEditor();
      Document document = editor.getDocument();
      FileStatusMap fileStatusMap = fixture.getDaemonCodeAnalyzer().getFileStatusMap();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        assertNull(fileStatusMap.getFileDirtyScope(document, file, Pass.LOCAL_INSPECTIONS));

        int offset = editor.getCaretModel().getOffset();
        fixture.type(' ');
        PsiDocumentManager.getInstance(project).commitAllDocuments(); // reset "defensively marked"
        testDaemon.waitForUpdateFileStatusBackgroundQueueInTests();
        assertEquals(new TextRange(offset-1, offset+1), fileStatusMap.getFileDirtyScope(document, file, Pass.LOCAL_INSPECTIONS));
      });
    });
  }

  @Test
  public void testTypingDoesNotMarkOtherFilesDirty() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String otherText = "class Other { void foo() { int i = 0; } }";
      PsiFile other = fixture.createFile("Other.java", otherText);
      @Language("JAVA")
      String text = "class S { void foo() { int i = 0;<caret> } }";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Editor editor = fixture.getEditor();
      Editor otherEditor = openEditor(fixture, other.getVirtualFile());
      PsiFile otherPsiFile = ReadAction.computeBlocking(() -> PsiDocumentManager.getInstance(project).getPsiFile(otherEditor.getDocument()));
      fixture.setActiveEditors(editor, otherEditor);
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> testDaemon.waitHighlighting(otherPsiFile, HighlightSeverity.ERROR));
      fixture.waitHighlighting(HighlightSeverity.ERROR);
      FileStatusMap fileStatusMap = fixture.getDaemonCodeAnalyzer().getFileStatusMap();
      assertNull(ReadAction.computeBlocking(() -> fileStatusMap.getFileDirtyScope(otherEditor.getDocument(), otherPsiFile, Pass.UPDATE_ALL)));

      // Only the edited editor is active, so the daemon does not re-highlight the other file and clean its status.
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.setActiveEditors(editor);
        fixture.type(' ');
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        testDaemon.waitForUpdateFileStatusBackgroundQueueInTests();
        assertNotNull(fileStatusMap.getFileDirtyScope(editor.getDocument(), file, Pass.UPDATE_ALL));
        assertNull(fileStatusMap.getFileDirtyScope(otherEditor.getDocument(), otherPsiFile, Pass.UPDATE_ALL));
      });
    });
  }

  private static @NotNull Editor openEditor(@NotNull DaemonRespondToChangesFixture fixture, @NotNull VirtualFile file) {
    Project project = fixture.getProject();
    fixture.allowTreeAccessForFile(file);
    return EdtTestUtil.runInEdtAndGet(() -> {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      Editor editor = Objects.requireNonNull(FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, 0), false));
      ((EditorImpl)editor).setCaretActive();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      DaemonCodeAnalyzerEx.getInstanceEx(project).restart("FileStatusMapTest.openEditor " + file);
      return editor;
    });
  }

  @Test
  public void testDefensivelyDirtyFlagDoesNotClearPrematurely() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      class Fac implements TextEditorHighlightingPassFactory {
        @Override
        public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
          return null;
        }
      }
      TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(fixture.getProject());
      registrar.registerTextEditorHighlightingPass(new Fac(), null, null, false, -1);

      @Language("JAVA")
      String text = "@Deprecated<caret> class S { } ";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      List<HighlightInfo> infos = fixture.waitHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
      assertThat(infos).hasSize(2);

      assertEquals("@Deprecated", infos.get(0).getText());
      assertEquals("S", infos.get(1).getText());

      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.backspace();
        fixture.type('d');
      });

      List<HighlightInfo> after = fixture.waitHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);

      assertEquals("@Deprecated", after.get(0).getText());
      assertEquals("S", after.get(1).getText());

      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.backspace();
        fixture.type('d');

        editor.getCaretModel().moveToOffset(editor.getDocument().getTextLength());
        fixture.type(" ");
      });

      after = fixture.waitHighlighting(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
      assertThat(after).hasSize(2);

      assertEquals("@Deprecated", after.get(0).getText());
      assertEquals("S", after.get(1).getText());
    });
  }


  @Test
  public void testModificationInExcludedFileDoesNotCauseRehighlight() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      long modCountBefore = ProjectRootManager.getInstance(project).getModificationCount();
      @Language("JAVA")
      String text = "class EEE { void f(){} }";
      VirtualFile excludedFile = fixture.configureByText(JavaFileType.INSTANCE, text).getVirtualFile();
      Document excludedDocument = ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(excludedFile));
      WriteCommandAction.runWriteCommandAction(project, () -> excludedDocument.insertString(0, " "));
      PsiTestUtil.addExcludedRoot(fixture.getModule(), excludedFile.getParent());
      assertTrue(ReadAction.computeBlocking(() -> ProjectFileIndex.getInstance(project).isExcluded(excludedFile)));

      @Language("JAVA")
      String text2 = "class X { <caret> }";
      fixture.configureByText(JavaFileType.INSTANCE, text2);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      PsiFile file = fixture.getFile();
      Document document = fixture.getEditor().getDocument();
      FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap();
      TextRange scope = ReadAction.computeBlocking(() -> me.getFileDirtyScope(document, file, Pass.UPDATE_ALL));
      assertNull(scope);

      assertTrue(ReadAction.computeBlocking(() -> ProjectFileIndex.getInstance(project).isExcluded(excludedFile)));
      assertNotEquals(modCountBefore, ProjectRootManager.getInstance(project).getModificationCount());
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        testDaemon.waitUpdateExpensiveFlags(excludedDocument);
        assertTrue(testDaemon.isMarkedExcluded(excludedDocument));
        assertFalse(testDaemon.isMarkedExcluded(document));
      });

      WriteCommandAction.runWriteCommandAction(project, () -> ((PsiJavaFile)PsiManager.getInstance(project).findFile(excludedFile)).getClasses()[0].getMethods()[0].delete());

      EdtTestUtil.runInEdtAndWait(() -> {
        testDaemon.waitUpdateExpensiveFlags(excludedDocument);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertNull(me.getFileDirtyScope(document, file, Pass.UPDATE_ALL));
      });
    });
  }

  @Test
  public void testModificationInWorkspaceXmlDoesNotCauseRehighlight() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class X { <caret> }";
      fixture.configureByText(JavaFileType.INSTANCE, text);
      EdtTestUtil.runInEdtAndWait(() -> RefreshQueue.getInstance().refresh(false, false, null)); // otherwise during app save below refresh might start sudden write action and cancel save

      Project project = fixture.getProject();
      PsiFile file = fixture.getFile();
      Document document = fixture.getEditor().getDocument();
      TestDaemonCodeAnalyzerImpl testDaemon = fixture.getTestDaemonCodeAnalyzer();
      StoreUtilKt.runInAllowSaveMode(true, () -> {
        EdtTestUtil.runInEdtAndWait(() -> {
          StoreUtil.saveDocumentsAndProjectsAndApp(true);
          ActionsOnSaveTestUtil.waitForActionsOnSaveToFinish(project);
        });
        VirtualFile workspaceFile = Objects.requireNonNull(project.getWorkspaceFile());
        Document excludedDocument = ReadAction.computeBlocking(() -> {
          PsiFile excludedPsiFile = Objects.requireNonNull(PsiManager.getInstance(project).findFile(workspaceFile));
          return Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(excludedPsiFile));
        });
        WriteCommandAction.runWriteCommandAction(project, () -> excludedDocument.insertString(0, " "));
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
        FileStatusMap me = DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap();
        TextRange scope = ReadAction.computeBlocking(() -> me.getFileDirtyScope(document, file, Pass.UPDATE_ALL));
        assertNull(scope);

        WriteCommandAction.runWriteCommandAction(project, () -> {
          excludedDocument.insertString(0, "<!-- dsfsd -->");
          PsiDocumentManager.getInstance(project).commitAllDocuments();
        });
        EdtTestUtil.runInEdtAndWait(() -> {
          try {
            testDaemon.waitUpdateExpensiveFlags(document);
            testDaemon.waitUpdateExpensiveFlags(excludedDocument);
          }
          catch (TimeoutException e) {
            throw new RuntimeException(e);
          }
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          assertNull(me.getFileDirtyScope(document, file, Pass.UPDATE_ALL));
        });
        return Unit.INSTANCE;
      });
    });
  }

  @Test
  public void testFileReload() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      VirtualFile file = fixture.createFile("a.java", "").getVirtualFile();
      Document document = getDocument(file);
      assertNotNull(document);

      FileStatusMap fileStatusMap = fixture.getDaemonCodeAnalyzer().getFileStatusMap();

      WriteCommandAction.runWriteCommandAction(project, () -> {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        GCWatcher.tracking(documentManager.getCachedPsiFile(document)).ensureCollected();
        assertNull(documentManager.getCachedPsiFile(document));

        @Language("JAVA")
        String text = "class X { void foo() {}}";
        document.insertString(0, text);
        documentManager.commitAllDocuments();
        assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, documentManager.getPsiFile(document), Pass.UPDATE_ALL));

        FileContentUtilCore.reparseFiles(file);
        assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, documentManager.getPsiFile(document), Pass.UPDATE_ALL));

        fixture.findClass("X").getMethods()[0].delete();
        assertEquals(TextRange.from(0, document.getTextLength()), fileStatusMap.getFileDirtyScope(document, documentManager.getPsiFile(document), Pass.UPDATE_ALL));
      });
    });
  }

  @Test
  public void testDocumentsMustBeWeaklyReferenced() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      VirtualFile file = fixture.createFile("a.java", "blah blah").getVirtualFile();
      AtomicReference<Document> document = new AtomicReference<>(getDocument(file));
      assertNotNull(document.get());
      AtomicReference<PsiFile> psiFile = new AtomicReference<>(ReadAction.computeBlocking(() -> PsiDocumentManager.getInstance(project).getPsiFile(document.get())));

      FileStatusMap fileStatusMap = fixture.getDaemonCodeAnalyzer().getFileStatusMap();
      String testName = fixture.getTestName(false);
      ReadAction.runBlocking(() -> {
        assertNull(fileStatusMap.getFileDirtyScopeForAllPassesCombined(document.get()));
        fileStatusMap.markWholeFileScopeDirty(document.get(), testName);
        assertEquals(psiFile.get().getTextRange(), fileStatusMap.getFileDirtyScope(document.get(), psiFile.get(), 0));
      });
      GCWatcher tracking = GCWatcher.tracking(document.get());
      document.set(null);
      psiFile.set(null);
      tracking.ensureCollected(); // WHOLE_RANGE_MARKER does not retain document

      document.set(getDocument(file));
      psiFile.set(ReadAction.computeBlocking(() -> PsiDocumentManager.getInstance(project).getPsiFile(document.get())));
      ReadAction.runBlocking(() -> {
        for (int pass = 1; pass<=Pass.LAST_PASS; pass++) {
          fileStatusMap.markFileUpToDate(document.get(), CodeInsightContextUtil.getCodeInsightContext(psiFile.get()), pass, new DaemonProgressIndicator());
        }
        for (int pass=1; pass<=Pass.LAST_PASS; pass++) {
          fileStatusMap.assertFileStatusScopeIsNull(document.get(), CodeInsightContextUtil.getCodeInsightContext(psiFile.get()), pass);
        }
      });
      TextRange range = new TextRange(1, 2);
      AppExecutorUtil.getAppExecutorService().submit(() -> ReadAction.runBlocking(()->fileStatusMap.markScopeDirty(document.get(), range, testName))).get();
      assertEquals(range, ReadAction.computeBlocking(() -> fileStatusMap.getFileDirtyScope(document.get(), psiFile.get(), Pass.EXTERNAL_TOOLS)));

      tracking = GCWatcher.tracking(document.get());
      document.set(null);
      psiFile.set(null);
      tracking.ensureCollected(); // fileStatusMap RangeMarker does not retain document

      document.set(getDocument(file));
      assertNull(fileStatusMap.getFileDirtyScopeForAllPassesCombined(document.get()));
    });
  }

  private static Document getDocument(@NotNull VirtualFile file) {
    return ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(file));
  }

  @Test
  public void testChangeDocumentFollowedByImmediateUndoMustDirtyTheInvolvedLinesBecauseRangeHighlightersMightBeDestroyedAndThenNotRestored() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "blah\nblah<caret>\nblah";
      PsiFile psiFile = fixture.configureByText(JavaFileType.INSTANCE, text);
      Document document = psiFile.getFileDocument();

      fixture.waitHighlighting(HighlightSeverity.ERROR);
      assertNull(ReadAction.computeBlocking(() -> FileStatusMap.getDirtyTextRange(document, psiFile, Pass.LOCAL_INSPECTIONS)));

      Editor editor = fixture.getEditor();
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE, editor);
        fixture.executeAction(IdeActions.ACTION_UNDO, editor);

        assertEquals(psiFile.getTextRange(), FileStatusMap.getDirtyTextRange(document, psiFile, Pass.LOCAL_INSPECTIONS));
      });
    });
  }

  @Test
  public void testAfterNoPsiChangeTheWholeFileShouldBeDirty() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "@Deprecated<caret> class S { } ";
      PsiFile psiFile = fixture.configureByText(JavaFileType.INSTANCE, text);
      Document document = psiFile.getFileDocument();
      fixture.waitHighlighting(HighlightSeverity.ERROR);
      assertNull(ReadAction.computeBlocking(() -> FileStatusMap.getDirtyTextRange(document, psiFile, Pass.UPDATE_ALL)));

      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.backspace();
        fixture.type('d');

        assertEquals(psiFile.getTextRange(), FileStatusMap.getDirtyTextRange(document, psiFile, Pass.UPDATE_ALL));
      });
    });
  }

  @Test
  public void testPsiTouchedScopes() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = """
        import java.util.*;
        class S {
          void f() {
            Map s1 = new HashMap();
            Map s2 = <caret>new HashMap();
            Map s3 = new HashMap();
          }
         }""";
      PsiFile psiFile = fixture.configureByText(JavaFileType.INSTANCE, text);
      Document document = psiFile.getFileDocument();
      fixture.waitHighlighting(HighlightSeverity.ERROR);
      assertNull(ReadAction.computeBlocking(() -> FileStatusMap.getDirtyTextRange(document, psiFile, Pass.UPDATE_ALL)));

      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.type('3');

        assertEquals(psiFile.getTextRange(), FileStatusMap.getDirtyTextRange(document, psiFile, Pass.UPDATE_ALL));
      });
    });
  }

  // An EditorTextField's own document is backed by a light file. The daemon highlights it, but the only invalidation it gets
  // for an edit is the narrow typing scope, so an annotator that looks wider than the typed characters never runs again and
  // its warning goes stale. Only a document its owner declared an interactive editing surface is invalidated like an
  // editor's. See IJAI-1049.
  @Test
  public void testEditInNonPhysicalDocumentDeclaredAnInteractiveEditingSurface() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(PlainTextLanguage.INSTANCE,
                                                        new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyTyppoAnnotator()},
                                                        () -> {
      Project project = fixture.getProject();
      Document document = configureNonPhysicalLightFile(fixture);
      PsiFile file = fixture.getFile();
      DaemonCodeAnalyzerImpl daemon = fixture.getDaemonCodeAnalyzer();
      try {
        document.putUserData(DaemonCodeAnalyzer.INTERACTIVE_NON_PHYSICAL_DOCUMENT, project);
        daemon.restart(fixture.getTestName(false));
        fixture.waitHighlighting(HighlightSeverity.WARNING);
        assertThat(ReadAction.computeBlocking(() -> MyTyppoAnnotator.myHighlights(project, document))).hasSize(1);
        assertNull(ReadAction.computeBlocking(() -> daemon.getFileStatusMap().getFileDirtyScope(document, file, Pass.UPDATE_ALL)));

        EdtTestUtil.runInEdtAndWait(() -> {
          // spell the flagged word differently, the way a user corrects a typo by hand
          typeInsideTheFlaggedWord(fixture, document);

          // the edit alone invalidated the document - no restart(), no doHighlighting(), nothing that marks it dirty for the test
          assertNotNull(daemon.getFileStatusMap().getFileDirtyScope(document, file, Pass.UPDATE_ALL));
        });
        fixture.waitHighlighting(HighlightSeverity.WARNING);
        assertThat(ReadAction.computeBlocking(() -> MyTyppoAnnotator.myHighlights(project, document))).isEmpty();
      }
      finally {
        Editor editor = fixture.getEditor();
        EdtTestUtil.runInEdtAndWait(() -> EditorFactory.getInstance().releaseEditor(editor));
      }
    }));
  }

  // the other half of the contract, and the bug the declaration above fixes: a light document that declared nothing keeps its
  // stale warning, so a preview or a console pays nothing for invalidation it never asked for
  @Test
  public void testEditInNonPhysicalDocumentThatDeclaredNothingKeepsItsStaleHighlighting() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(PlainTextLanguage.INSTANCE,
                                                        new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyTyppoAnnotator()},
                                                        () -> {
      Project project = fixture.getProject();
      Document document = configureNonPhysicalLightFile(fixture);
      try {
        fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
        fixture.waitHighlighting(HighlightSeverity.WARNING);
        assertThat(ReadAction.computeBlocking(() -> MyTyppoAnnotator.myHighlights(project, document))).hasSize(1);

        EdtTestUtil.runInEdtAndWait(() -> typeInsideTheFlaggedWord(fixture, document));
        fixture.waitHighlighting(HighlightSeverity.WARNING);

        assertThat(ReadAction.computeBlocking(() -> MyTyppoAnnotator.myHighlights(project, document))).hasSize(1);
      }
      finally {
        Editor editor = fixture.getEditor();
        EdtTestUtil.runInEdtAndWait(() -> EditorFactory.getInstance().releaseEditor(editor));
      }
    }));
  }

  // exactly how EditorTextField.createDocument() builds its document: a light file with the event system on
  private static @NotNull Document configureNonPhysicalLightFile(@NotNull DaemonRespondToChangesFixture fixture) {
    PsiFile lightFile = ReadAction.computeBlocking(() -> PsiFileFactory.getInstance(fixture.getProject())
      .createFileFromText("Dummy.txt", PlainTextFileType.INSTANCE, "a " + MyTyppoAnnotator.TYPPO + " here",
                          LocalTimeCounter.currentTime(), true, false));
    fixture.configureByExistingFile(lightFile.getVirtualFile());
    PsiFile file = fixture.getFile();
    assertFalse(ReadAction.computeBlocking(() -> file.getViewProvider().correspondsToRealFile()));
    Editor editor = fixture.getEditor();
    Document document = editor.getDocument();
    EdtTestUtil.runInEdtAndWait(() -> VisibleHighlightingPassFactory.setVisibleRangeForHeadlessMode(editor, new ProperTextRange(0, document.getTextLength())));
    return document;
  }

  @RequiresEdt
  private static void typeInsideTheFlaggedWord(@NotNull DaemonRespondToChangesFixture fixture, @NotNull Document document) {
    fixture.getEditor().getCaretModel().moveToOffset(document.getText().indexOf(MyTyppoAnnotator.TYPPO) + 1);
    fixture.type('x');
    PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
    fixture.getTestDaemonCodeAnalyzer().waitForUpdateFileStatusBackgroundQueueInTests();
  }

  // flags one misspelled word - the smallest stand-in for the spell checker and the grammar checker, which report through annotators
  public static class MyTyppoAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    static final String TYPPO = "typpo";
    private static final String MSG = "Typo: " + TYPPO;

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      iDidIt();
      if (!(element instanceof PsiFile)) return;
      int start = element.getText().indexOf(TYPPO);
      if (start < 0) return;
      holder.newAnnotation(HighlightSeverity.WARNING, MSG).range(TextRange.from(start, TYPPO.length())).create();
    }

    static @NotNull List<HighlightInfo> myHighlights(@NotNull Project project, @NotNull Document document) {
      return ContainerUtil.filter(DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project),
                                  info -> MSG.equals(info.getDescription()));
    }
  }
}
