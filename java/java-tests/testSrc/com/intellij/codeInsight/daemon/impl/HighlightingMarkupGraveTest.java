// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.CoroutineKt;
import com.intellij.testFramework.LeakHunter;
import com.intellij.util.TestTimeOut;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCWatcher;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.codeInsight.daemon.impl.HelperKt.openTextEditorForDaemonTest;
import static com.intellij.openapi.fileEditor.impl.EditorHistoryManagerTestKt.overrideFileEditorManagerImplementation;

public class HighlightingMarkupGraveTest extends DaemonAnalyzerTestCase {
  @Override
  protected boolean doTestLineMarkers() {
    return true;
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

  @Override
  protected void setUpProject() throws Exception {
    overrideFileEditorManagerImplementation(FileEditorManagerImpl.class, getTestRootDisposable());

    super.setUpProject();
  }

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
        configureByText(JavaFileType.INSTANCE, text);
        assertEquals(MyStoppableAnnotator.SWEARING, assertOneElement(highlightErrors()).getDescription());
        assertEquals("//XXX", assertOneElement(highlightErrors()).getHighlighter().getTextRange().substring(getFile().getText()));

        VirtualFile virtualFile = getFile().getVirtualFile();
        closeEditorAndEnsureTheDocumentMarkupIsGced(virtualFile);

        annotator.allowToRun.set(false);

        // reload file editor, and check the stored highlighters are reloaded back and applied, before the highlighting (MyStoppableAnnotator in particular) is run
        try {
          TextEditor textEditor = openTextEditorForDaemonTest(myProject, virtualFile);
          assertNotNull(textEditor);
          Document document = textEditor.getEditor().getDocument();
          MarkupModel markupModel = DocumentMarkupModel.forDocument(document, getProject(), true);

          CoroutineKt.executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject);

          List<String> symbolHighlighters =
            Arrays.stream(markupModel.getAllHighlighters())
              .filter(h -> h.getTextAttributesKey() != null)
              .filter(h -> h.getLayer() == HighlighterLayer.ADDITIONAL_SYNTAX)
              .filter(h -> HighlightingNecromancer.isZombieMarkup(h))
              .sorted(RangeMarker.BY_START_OFFSET)
              .map(h -> h.getTextRange().substring(document.getText()))
              .toList();
          assertNotEmpty(symbolHighlighters);

          assertEquals("[ClassName, fieldName, methodName, ClassName, paramName]", symbolHighlighters.toString());
        }
        finally {
          annotator.allowToRun.set(true);
        }
      });
    });
  }

  private void closeEditorAndEnsureTheDocumentMarkupIsGced(@NotNull VirtualFile virtualFile) {
    // close editor, and clear all references retaining this document
    // (to make sure the DocumentMarkupModel is really recreated and populated with stored highlighters, not preserved since the previous highlighting run)
    FileDocumentManager.getInstance().saveAllDocuments();
    FileEditorManager.getInstance(myProject).closeFile(virtualFile);
    HighlightingNecromancer.clearSpawnedZombies(getProject());

    myFile = null;
    myEditor = null;
    TestTimeOut t = TestTimeOut.setTimeout(100 * 2, TimeUnit.MILLISECONDS);
    while (!t.isTimedOut()) {
      CoroutineKt.executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject);
      LaterInvocator.purgeExpiredItems();
      LaterInvocator.dispatchPendingFlushes();
      ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).getUpdateProgress().clear();
    }
    try {
      GCWatcher.tracking(FileDocumentManager.getInstance().getDocument(virtualFile)).ensureCollected();
    }
    catch (IllegalStateException e) {
      LeakHunter.checkLeak(LeakHunter.allRoots(), DocumentImpl.class, doc -> FileDocumentManager.getInstance().getFile(doc) == virtualFile);
      throw e;
    }
  }

  public void testStoredHighlightersAreAppliedImmediatelyOnFileReload() {
    HighlightingNecromancer.runInEnabled(() -> {
      MyStoppableAnnotator annotator = new MyStoppableAnnotator();
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyStoppableAnnotator[]{annotator}, () -> {
        @Language("JAVA")
        String text = """
          class X {
           //XXX
          }""";
        configureByText(JavaFileType.INSTANCE, text);
        assertEquals(MyStoppableAnnotator.SWEARING, assertOneElement(highlightErrors()).getDescription());
        assertEquals("//XXX", assertOneElement(highlightErrors()).getHighlighter().getTextRange().substring(getFile().getText()));

        VirtualFile virtualFile = getFile().getVirtualFile();
        closeEditorAndEnsureTheDocumentMarkupIsGced(virtualFile);

        annotator.allowToRun.set(false);

        // reload file editor, and check the stored highlighters are reloaded back and applied, before the highlighting (MyStoppableAnnotator in particular) is run
        try {
          TextEditor textEditor = openTextEditorForDaemonTest(myProject, virtualFile);
          assertNotNull(textEditor);
          Document document = textEditor.getEditor().getDocument();
          MarkupModel markupModel = DocumentMarkupModel.forDocument(document, getProject(), true);
          CoroutineKt.executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject);

          RangeHighlighter
            errorHighlighter = ContainerUtil.find(markupModel.getAllHighlighters(), h -> CodeInsightColors.ERRORS_ATTRIBUTES.equals(h.getTextAttributesKey()));
          assertNotNull(errorHighlighter);
          assertEquals("//XXX", errorHighlighter.getTextRange().substring(document.getText()));
          assertTrue(HighlightingNecromancer.isZombieMarkup(errorHighlighter));
        }
        finally {
          annotator.allowToRun.set(true);
        }
      });
    });
  }
}

