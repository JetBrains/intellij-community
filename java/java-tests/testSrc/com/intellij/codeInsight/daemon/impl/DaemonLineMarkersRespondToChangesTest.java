// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * tests general daemon behaviour/interruptibility/restart during highlighting
 */
@SkipSlowTestLocally
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class DaemonLineMarkersRespondToChangesTest extends DaemonAnalyzerTestCase {
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
  protected void setUpProject() throws Exception {
    super.setUpProject();
    // treat listeners added there as not leaks
    EditorMouseHoverPopupManager.getInstance();
  }

  public void testOverriddenMethodMarkers() throws Exception {
    configureByFile(DaemonRespondToChangesTest.BASE_PATH + getTestName(false) + ".java");
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
    configureByFile(DaemonRespondToChangesTest.BASE_PATH + "OverriddenMethodMarkers.java");
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

  public void testLineMarkersReuse() throws Throwable {
    configureByFile(DaemonRespondToChangesTest.BASE_PATH + "LineMarkerChange.java");

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

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  public void testLineMarkersClearWhenTypingAtTheEndOfPsiComment() {
    configureByText(JavaFileType.INSTANCE, "class S {\n//ddd<caret>\n}");
    StringBuffer log = new StringBuffer();
    // highlight all PsiComments
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

  public void testLineMarkerRegisteredOnWrongPsiElementForExampleOnFileLevelYeahCrazyIKnowMustNotRemoveItselfOnTypingOutsideTheLineMarkerRange() {
    configureByText(JavaFileType.INSTANCE, """
      class X {
        // blah
        int foo;
      }
      <caret>
      """);

    GutterIconNavigationHandler<PsiFile> MY_NAVIGATION_HANDLER = (_1, _2) -> { };
    LineMarkerProvider provider = element -> {
      if (element instanceof PsiFile psiFile) {
        return new LineMarkerInfo<>(psiFile, element.getTextRange(), AllIcons.Ide.Dislike, (_3) -> "my tooltip", MY_NAVIGATION_HANDLER, GutterIconRenderer.Alignment.LEFT, () -> "");
      }
      return null;
    };
    LineMarkerProviders.getInstance().addExplicitExtension(JavaLanguage.INSTANCE, provider, getTestRootDisposable());
    myDaemonCodeAnalyzer.restart();

    {
      assertEmpty(doHighlighting(HighlightSeverity.ERROR));
      LineMarkerInfo<?> info = assertOneElement(DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject()));
      assertSame(MY_NAVIGATION_HANDLER, info.getNavigationHandler());
    }

    type("\n\n\n\n\n\n\n\n\n\n");

    {
      assertEmpty(doHighlighting(HighlightSeverity.ERROR));
      LineMarkerInfo<?> info = assertOneElement(DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject()));
      assertSame(MY_NAVIGATION_HANDLER, info.getNavigationHandler());
    }
    type("\n\n\n\n\n\n\n\n\n\n");

    {
      assertEmpty(doHighlighting(HighlightSeverity.ERROR));
      LineMarkerInfo<?> info = assertOneElement(DaemonCodeAnalyzerImpl.getLineMarkers(myEditor.getDocument(), getProject()));
      assertSame(MY_NAVIGATION_HANDLER, info.getNavigationHandler());
    }

  }
}
