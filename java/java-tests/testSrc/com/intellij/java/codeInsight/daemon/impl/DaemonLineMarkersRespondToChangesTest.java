// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.CanChangeDocumentDuringHighlighting;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EdtInvocationManager;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesFixture.daemonRespondToChangesFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * tests general daemon behaviour/interruptibility/restart during highlighting
 */
@SkipSlowTestLocally
@CanChangeDocumentDuringHighlighting
@TestApplication
public class DaemonLineMarkersRespondToChangesTest {
  private final TestFixture<DaemonRespondToChangesFixture> daemonFixture = daemonRespondToChangesFixture();

  @BeforeEach
  void setUp() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.setTestLineMarkers(true);
    fixture.enableInspectionTool(new UnusedDeclarationInspection());
    UndoManager.getInstance(fixture.getProject());
  }

  @Test
  public void testOverriddenMethodMarkers() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.configureByFile(DaemonRespondToChangesTest.BASE_PATH + fixture.getTestName(false) + ".java");
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      Document document = fixture.getEditor().getDocument();
      List<LineMarkerInfo<?>> markers = getDisplayedLineMarkers(document, fixture.getProject());
      assertThat(markers).hasSize(3);

      fixture.type("//xxxx");

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      markers = getDisplayedLineMarkers(document, fixture.getProject());
      assertThat(markers).hasSize(3);
    });
  }


  @Test
  public void testOverriddenMethodMarkersDoNotClearedByChangingWhitespaceNearby() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.configureByFile(DaemonRespondToChangesTest.BASE_PATH + "OverriddenMethodMarkers.java");
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      Editor editor = fixture.getEditor();
      Document document = editor.getDocument();
      List<LineMarkerInfo<?>> markers = getDisplayedLineMarkers(document, fixture.getProject());
      assertEquals(3, markers.size(), markers.toString());

      PsiFile file = fixture.getFile();
      int offset = ReadAction.computeBlocking(() -> {
        PsiElement element = ((PsiJavaFile)file).getClasses()[0].findMethodsByName("f", false)[0].getReturnTypeElement().getNextSibling();
        assertEquals("   ", element.getText());
        return element.getTextOffset() + 1;
      });
      EdtTestUtil.runInEdtAndWait(() -> editor.getCaretModel().moveToOffset(offset));
      fixture.type(" ");

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      markers = getDisplayedLineMarkers(document, fixture.getProject());
      assertEquals(3, markers.size(), markers.toString());
    });
  }

  @Test
  public void testLineMarkersReuse() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      fixture.configureByFile(DaemonRespondToChangesTest.BASE_PATH + "LineMarkerChange.java");

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      Editor editor = fixture.getEditor();
      @NotNull Document document1 = editor.getDocument();
      List<LineMarkerInfo<?>> lineMarkers = getDisplayedLineMarkers(document1, project);
      assertThat(lineMarkers).hasSize(5);

      Collection<String> changed = new ArrayList<>();
      MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(fixture.getDocument(fixture.getFile()), project, true);
      Disposable testRootDisposable = fixture.getTestRootDisposable();
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.type('X');

        modelEx.addMarkupModelListener(testRootDisposable, new MarkupModelListener() {
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
              @NotNull Document document = editor.getDocument();
              List<LineMarkerInfo<?>> lineMarkers = LineMarkersPass.getDisplayedLineMarkers(document, project);
              if (ContainerUtil.find(lineMarkers, lm -> lm.highlighter == highlighter) != null) {
                changed.add(highlighter + ": \n" + reason);
              } // else not line marker
            });
          }
        });
      });

      List<HighlightInfo> infosAfter = fixture.waitHighlighting(HighlightSeverity.ERROR);
      assertThat(infosAfter).isNotEmpty();
      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
      assertThat(changed).isEmpty();
      @NotNull Document document = editor.getDocument();
      List<LineMarkerInfo<?>> lineMarkersAfter = getDisplayedLineMarkers(document, project);
      assertThat(lineMarkers).hasSize(lineMarkersAfter.size());
    });
  }

  @Test
  public void testLineMarkersDoNotBlinkOnBackSpaceRightBeforeMethodIdentifier() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      @Language("JAVA")
      String text = """
        package x;
        class  <caret>ToRun{
          public static void main(String[] args) {
          }
        }""";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      Editor editor = fixture.getEditor();
      @NotNull Document document1 = editor.getDocument();
      List<LineMarkerInfo<?>> lineMarkers = getDisplayedLineMarkers(document1, project);
      assertThat(lineMarkers).hasSize(2);

      Collection<String> changed = Collections.synchronizedList(new ArrayList<>());
      MarkupModelEx modelEx = (MarkupModelEx)DocumentMarkupModel.forDocument(fixture.getDocument(fixture.getFile()), project, true);
      Disposable testRootDisposable = fixture.getTestRootDisposable();
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.backspace();

        modelEx.addMarkupModelListener(testRootDisposable, new MarkupModelListener() {
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
            @NotNull Document document = editor.getDocument();
            List<LineMarkerInfo<?>> lineMarkers = LineMarkersPass.getDisplayedLineMarkers(document, project);
            if (ContainerUtil.find(lineMarkers, lm -> lm.highlighter == highlighter) != null) {
              changed.add(highlighter + ": \n" + ExceptionUtil.getThrowableText(new Throwable(reason)));
            } // else not line marker
          }
        });
      });

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
      @NotNull Document document = editor.getDocument();
      assertThat(getDisplayedLineMarkers(document, project)).hasSize(2);

      assertThat(changed).isEmpty();
    });
  }

  @Test
  public void testLineMarkersClearWhenTypingAtTheEndOfPsiComment() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      @Language("JAVA")
      String text = "class S {\n//ddd<caret>\n}";
      fixture.configureByText(JavaFileType.INSTANCE, text);
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
      LineMarkerProviders.getInstance().addExplicitExtension(JavaLanguage.INSTANCE, provider, fixture.getTestRootDisposable());
      Editor editor = fixture.getEditor();
      PsiFile file = fixture.getFile();
      String testName = fixture.getTestName(false);
      try {
        EdtTestUtil.runInEdtAndWait(() -> {
          fixture.getDaemonCodeAnalyzer().restart(testName);
          TextRange range = Objects.requireNonNull(FileStatusMap.getDirtyTextRange(editor.getDocument(), file, Pass.UPDATE_ALL));
          log.append("FileStatusMap.getDirtyTextRange: " + range+"\n");
          List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(file, range.getStartOffset(), range.getEndOffset());
          log.append("CollectHighlightsUtil.getElementsInRange: " + range + ": " + elements.size() +" elements : "+ elements+"\n");
        });
        List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.INFORMATION);
        log.append(" File text: '" + getText(file) + "'\n");
        log.append("infos: " + infos + "\n");
        assertThat(DaemonAnalyzerFixture.filter(infos, HighlightSeverity.ERROR)).isEmpty();

        @NotNull Document document2 = editor.getDocument();
        List<LineMarkerInfo<?>> lineMarkers = getDisplayedLineMarkers(document2, project);
        assertThat(lineMarkers).hasSize(1);

        fixture.type(' ');
        infos = fixture.waitHighlighting(HighlightSeverity.INFORMATION);
        log.append("File text: '" + getText(file) + "'\n");
        log.append("infos: " + infos + "\n");
        assertThat(DaemonAnalyzerFixture.filter(infos, HighlightSeverity.ERROR)).isEmpty();

        @NotNull Document document1 = editor.getDocument();
        lineMarkers = getDisplayedLineMarkers(document1, project);
        assertThat(lineMarkers).hasSize(1);

        fixture.backspace();
        infos = fixture.waitHighlighting(HighlightSeverity.INFORMATION);
        log.append("File text: '" + getText(file) + "'\n");
        log.append("infos: " + infos + "\n");
        assertThat(DaemonAnalyzerFixture.filter(infos, HighlightSeverity.ERROR)).isEmpty();

        @NotNull Document document = editor.getDocument();
        lineMarkers = getDisplayedLineMarkers(document, project);
        assertThat(lineMarkers).hasSize(1);
      }
      catch (AssertionError e) {
        System.err.println("Log:\n"+log+"\n---");
        throw e;
      }
    });
  }

  @Test
  public void testLineMarkerRegisteredOnWrongPsiElementForExampleOnFileLevelYeahCrazyIKnowMustNotRemoveItselfOnTypingOutsideTheLineMarkerRange() {
    DaemonRespondToChangesFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      Project project = fixture.getProject();
      @Language("JAVA")
      String text = """
        class X {
          // blah
          int foo;
        }
        <caret>
        """;
      fixture.configureByText(JavaFileType.INSTANCE, text);

      GutterIconNavigationHandler<PsiFile> MY_NAVIGATION_HANDLER = (_, _) -> { };
      LineMarkerProvider provider = element -> {
        if (element instanceof PsiFile psiFile) {
          return new LineMarkerInfo<>(psiFile, element.getTextRange(), AllIcons.Ide.Dislike, (_) -> "my tooltip", MY_NAVIGATION_HANDLER, GutterIconRenderer.Alignment.LEFT, () -> "");
        }
        return null;
      };
      LineMarkerProviders.getInstance().addExplicitExtension(JavaLanguage.INSTANCE, provider, fixture.getTestRootDisposable());
      fixture.getDaemonCodeAnalyzer().restart(fixture.getTestName(false));
      Document document = fixture.getEditor().getDocument();

      {
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
        List<LineMarkerInfo<?>> markers = getDisplayedLineMarkers(document, project);
        assertThat(markers).hasSize(1);
        assertSame(MY_NAVIGATION_HANDLER, markers.getFirst().getNavigationHandler());
      }

      fixture.type("\n\n\n\n\n\n\n\n\n\n");

      {
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
        List<LineMarkerInfo<?>> markers = getDisplayedLineMarkers(document, project);
        assertThat(markers).hasSize(1);
        assertSame(MY_NAVIGATION_HANDLER, markers.getFirst().getNavigationHandler());
      }
      fixture.type("\n\n\n\n\n\n\n\n\n\n");

      {
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
        List<LineMarkerInfo<?>> markers = getDisplayedLineMarkers(document, project);
        assertThat(markers).hasSize(1);
        assertSame(MY_NAVIGATION_HANDLER, markers.getFirst().getNavigationHandler());
      }
    });
  }

  private static @NotNull List<LineMarkerInfo<?>> getDisplayedLineMarkers(@NotNull Document document, @NotNull Project project) {
    return ReadAction.computeBlocking(() -> LineMarkersPass.getDisplayedLineMarkers(document, project));
  }

  private static @NotNull String getText(@NotNull PsiFile file) {
    return ReadAction.computeBlocking(() -> file.getText());
  }
}
