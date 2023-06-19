// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LeakHunter;
import com.intellij.util.TestTimeOut;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HighlightingMarkupGraveTest extends DaemonAnalyzerTestCase {
  @Override
  protected boolean doTestLineMarkers() {
    return true;
  }

  public static class MyStoppableAnnotator extends DaemonRespondToChangesTest.MyRecordingAnnotator {
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
  public void testStoredHighlightersAreAppliedImmediatelyOnFileReload() {
    MyStoppableAnnotator annotator = new MyStoppableAnnotator();
    DaemonRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new MyStoppableAnnotator[]{annotator}, () -> {
      @Language("JAVA")
      String text = """
        class X {
         //XXX
        }""";
      configureByText(JavaFileType.INSTANCE, text);
      assertEquals(MyStoppableAnnotator.SWEARING, assertOneElement(highlightErrors()).getDescription());
      assertEquals("//XXX", assertOneElement(highlightErrors()).highlighter.getTextRange().substring(getFile().getText()));

      HighlightingMarkupGrave markupRestorer = getProject().getService(HighlightingMarkupGrave.class);
      Element savedState = markupRestorer.getState();
      markupRestorer.loadState(savedState); // emulate save on exit - load on open, without explicit close/reload project components

      // close editor, and clear all references retaining this document
      // (to make sure the DocumentMarkupModel is really recreated and populated with stored highlighters, not preserved since the previous highlighting run)
      FileDocumentManager.getInstance().saveAllDocuments();
      VirtualFile virtualFile = getFile().getVirtualFile();
      FileEditorManager.getInstance(myProject).closeFile(virtualFile);

      myFile = null;
      
      myEditor = null;
      TestTimeOut t = TestTimeOut.setTimeout(100 * 2, TimeUnit.MILLISECONDS);
      while (!t.isTimedOut()) {
        UIUtil.dispatchAllInvocationEvents();
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

      annotator.allowToRun.set(false);

      // reload file editor, and check the stored highlighters are reloaded back and applied, before the highlighting (MyStoppableAnnotator in particular) is run
      try {
        TextEditor textEditor = (TextEditor)ContainerUtil.find(FileEditorManager.getInstance(myProject).openFile(virtualFile), f->f instanceof TextEditor);
        assertNotNull(textEditor);
        Document document = textEditor.getEditor().getDocument();
        MarkupModel markupModel = DocumentMarkupModel.forDocument(document, getProject(), true);
        UIUtil.dispatchAllInvocationEvents();
        RangeHighlighter errorHighlighter = ContainerUtil.find(markupModel.getAllHighlighters(), h -> CodeInsightColors.ERRORS_ATTRIBUTES.equals(h.getTextAttributesKey()));
        assertNotNull(errorHighlighter);
        assertEquals("//XXX", errorHighlighter.getTextRange().substring(document.getText()));
        assertTrue(HighlightingMarkupGrave.isZombieMarkup(errorHighlighter));
      }
      finally {
        annotator.allowToRun.set(true);
      }
    });
  }
}

