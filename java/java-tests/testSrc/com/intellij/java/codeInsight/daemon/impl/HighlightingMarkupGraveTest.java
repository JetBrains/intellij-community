// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightingNecromancer;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ide.progress.TasksKt;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.CoroutineKt;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.TestTimeOut;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCWatcher;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static com.intellij.openapi.fileEditor.impl.EditorHistoryManagerTestKt.overrideFileEditorManagerImplementation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class HighlightingMarkupGraveTest {
  private static Disposable fileEditorManagerDisposable;

  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeAll
  static void overrideFileEditorManager() {
    fileEditorManagerDisposable = Disposer.newDisposable("HighlightingMarkupGraveTest");
    overrideFileEditorManagerImplementation(FileEditorManagerImpl.class, fileEditorManagerDisposable);
  }

  @AfterAll
  static void restoreFileEditorManager() {
    Disposer.dispose(fileEditorManagerDisposable);
  }

  @BeforeEach
  void setUp() {
    daemonFixture.get().setTestLineMarkers(true);
  }

  public static class MyStoppableAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    private static final String SWEARING = "No swearing";
    private final AtomicBoolean allowToRun = new AtomicBoolean(true);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      while (!allowToRun.get()) {
        Thread.yield();
      }
      if (element instanceof PsiComment && element.getText().equals("//XXX")) {
        holder.newAnnotation(HighlightSeverity.ERROR, SWEARING).range(element).create();
        iDidIt();
      }
    }
  }

  @Test
  public void testSymbolSeverityHighlightersAreAppliedOnFileReload() {
    HighlightingNecromancer.runInEnabled(() -> {
      MyStoppableAnnotator annotator = new MyStoppableAnnotator();
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyStoppableAnnotator[]{annotator}, () -> {
        @Language("JAVA")
        String text = """
          class ClassName {
           //XXX
           int fieldName;
           void methodName(ClassName paramName) {}
          }""";
        DaemonAnalyzerFixture fixture = daemonFixture.get();
        Project myProject = fixture.getProject();
        fixture.configureByText(JavaFileType.INSTANCE, text);
        assertSwearingIsHighlighted(fixture);

        VirtualFile virtualFile = fixture.getFile().getVirtualFile();
        closeEditorAndEnsureTheDocumentMarkupIsGced(virtualFile);

        annotator.allowToRun.set(false);

        // reload file editor, and check the stored highlighters are reloaded back and applied, before the highlighting (MyStoppableAnnotator in particular) is run
        try {
          EdtTestUtil.runInEdtAndWait(() -> {
            TextEditor textEditor = openTextEditorForDaemonTest(myProject, virtualFile);
            assertNotNull(textEditor);
            Document document = textEditor.getEditor().getDocument();
            MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myProject, true);

            CoroutineKt.executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject);

            List<String> symbolHighlighters =
              Arrays.stream(markupModel.getAllHighlighters())
                .filter(h -> h.isValid())
                .filter(h -> h.getTextAttributesKey() != null)
                .filter(h -> h.getLayer() == HighlighterLayer.ADDITIONAL_SYNTAX)
                .filter(h -> HighlightingNecromancer.isZombieMarkup(h))
                .sorted(RangeMarker.BY_START_OFFSET)
                .map(h -> h.getTextRange().substring(document.getText()))
                .toList();
            assertFalse(symbolHighlighters.isEmpty());

            assertEquals("[ClassName, fieldName, methodName, ClassName, paramName]", symbolHighlighters.toString());
          });
        }
        finally {
          annotator.allowToRun.set(true);
        }
      });
    });
  }

  /**
   * Checks the one error of {@link MyStoppableAnnotator}. The highlight infos stay in this frame only, so the document can be collected later.
   */
  private static void assertSwearingIsHighlighted(DaemonAnalyzerFixture fixture) {
    List<HighlightInfo> errors = fixture.highlightErrors();
    assertEquals(1, errors.size());
    assertEquals(MyStoppableAnnotator.SWEARING, errors.getFirst().getDescription());
    errors = fixture.highlightErrors();
    assertEquals(1, errors.size());
    String text = ReadAction.computeBlocking(() -> fixture.getFile().getText());
    assertEquals("//XXX", errors.getFirst().getHighlighter().getTextRange().substring(text));
  }

  private void closeEditorAndEnsureTheDocumentMarkupIsGced(@NotNull VirtualFile virtualFile) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    Project myProject = fixture.getProject();
    // close editor, and clear all references retaining this document
    // (to make sure the DocumentMarkupModel is really recreated and populated with stored highlighters, not preserved since the previous highlighting run)
    EdtTestUtil.runInEdtAndWait(() -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      FileEditorManager.getInstance(myProject).closeFile(virtualFile);

      fixture.setFile(null);
      fixture.setEditor(null);
      TestTimeOut t = TestTimeOut.setTimeout(100 * 2, TimeUnit.MILLISECONDS);
      while (!t.isTimedOut()) {
        CoroutineKt.executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject);
        LaterInvocator.purgeExpiredItems();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        fixture.getDaemonCodeAnalyzer().restart(this);
      }
      HighlightingNecromancer.clearSpawnedZombies(myProject);
      try {
        GCWatcher.tracking(FileDocumentManager.getInstance().getDocument(virtualFile)).ensureCollected();
      }
      catch (IllegalStateException e) {
        LeakHunter.checkLeak(LeakHunter.allRoots(), DocumentImpl.class, doc -> FileDocumentManager.getInstance().getFile(doc) == virtualFile);
        throw e;
      }
    });
  }

  @Test
  public void testStoredHighlightersAreAppliedImmediatelyOnFileReload() {
    HighlightingNecromancer.runInEnabled(() -> {
      MyStoppableAnnotator annotator = new MyStoppableAnnotator();
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyStoppableAnnotator[]{annotator}, () -> {
        @Language("JAVA")
        String text = """
          class X {
           //XXX
          }""";
        DaemonAnalyzerFixture fixture = daemonFixture.get();
        Project myProject = fixture.getProject();
        fixture.configureByText(JavaFileType.INSTANCE, text);
        assertSwearingIsHighlighted(fixture);

        VirtualFile virtualFile = fixture.getFile().getVirtualFile();
        closeEditorAndEnsureTheDocumentMarkupIsGced(virtualFile);

        annotator.allowToRun.set(false);

        // reload file editor, and check the stored highlighters are reloaded back and applied, before the highlighting (MyStoppableAnnotator in particular) is run
        try {
          EdtTestUtil.runInEdtAndWait(() -> {
            TextEditor textEditor = openTextEditorForDaemonTest(myProject, virtualFile);
            assertNotNull(textEditor);
            Document document = textEditor.getEditor().getDocument();
            MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myProject, true);
            CoroutineKt.executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject);

            RangeHighlighter errorHighlighter = ContainerUtil.find(markupModel.getAllHighlighters(), h -> h.isValid() && CodeInsightColors.ERRORS_ATTRIBUTES.equals(h.getTextAttributesKey()));
            assertNotNull(errorHighlighter);
            assertEquals("//XXX", errorHighlighter.getTextRange().substring(document.getText()));
            assertTrue(HighlightingNecromancer.isZombieMarkup(errorHighlighter));
          });
        }
        finally {
          annotator.allowToRun.set(true);
        }
      });
    });
  }

  private static TextEditor openTextEditorForDaemonTest(Project project, VirtualFile file) {
    List<FileEditor> editors = TasksKt.runWithModalProgressBlocking(project, "", (_, _) ->
      FileEditorManagerEx.getInstanceEx(project).openFile(file));
    return ContainerUtil.findInstance(editors, TextEditor.class);
  }
}

