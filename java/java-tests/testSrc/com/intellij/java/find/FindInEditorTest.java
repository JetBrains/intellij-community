// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.find;

import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.find.impl.livePreview.LivePreview;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class FindInEditorTest extends LightPlatformCodeInsightTestCase {
  private LivePreviewController myLivePreviewController;
  private FindModel myFindModel;

  private ByteArrayOutputStream myOutputStream;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFindModel = new FindModel();

    myOutputStream = new ByteArrayOutputStream();
    LivePreview.ourTestOutput = new PrintStream(myOutputStream);
    EditorHintListener listener = new EditorHintListener() {
      @Override
      public void hintShown(@NotNull Project project, @NotNull LightweightHint hint, int flags) {
        LivePreview.processNotFound();
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(EditorHintListener.TOPIC, listener);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFindModel = null;
      myOutputStream = null;
      if (myLivePreviewController != null) {
        myLivePreviewController.dispose();
        myLivePreviewController = null;
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
  protected boolean isRunInCommand() {
    return false;
  }

  private void initFind() {
    initFind(getEditor());
  }

  private void initFind(Editor editor) {
    SearchResults searchResults = new SearchResults(editor, editor.getProject());
    myLivePreviewController = new LivePreviewController(searchResults, null, getTestRootDisposable());
    myFindModel.addObserver(findModel -> myLivePreviewController.updateInBackground(myFindModel, true));
    myLivePreviewController.on();
  }

  public void testBasicFind() {
    configureFromText("ab");
    initFind();
    myFindModel.setStringToFind("a");
    checkResults();
    myFindModel.setStringToFind("a2");
    assertFalse(getEditor().getSelectionModel().hasSelection());
  }

  public void testEmacsLikeFallback() {
    configureFromText("a\nab");
    initFind();
    myFindModel.setStringToFind("a");
    myFindModel.setStringToFind("ab");
    myFindModel.setStringToFind("a");
    checkResults();
  }

  public void testKeepSelection() {
    configureFromText("""
                        one
                        two
                        three
                        four""");
    initFind();
    myFindModel.setStringToFind("two");
    checkResultByText("""
                        one
                        <selection>two</selection>
                        three
                        four""");
    getEditor().getSelectionModel().setSelection(2, 10);
    myFindModel.setGlobal(false); // search in selection
    myFindModel.setStringToFind("twox");
    checkResultByText("""
                        on<selection>e
                        two
                        th</selection>ree
                        four""");
  }
  
  public void testZeroWidthRegexpAtEndOfFile() {
    configureFromText("a\nb\nc");
    initFind();
    myFindModel.setRegularExpressions(true);
    myFindModel.setStringToFind("$");
    checkResults();
  }
  
  public void testSearchAdjacentOccurrences() {
    configureFromText("testtest");
    initFind();
    myFindModel.setRegularExpressions(true);
    myFindModel.setStringToFind("test");
    checkResults();
  }

  public void testReplacementWithEmptyString() throws FindManager.MalformedReplacementStringException {
    RegistryValue value = Registry.get("ide.find.show.replacement.hint.for.simple.regexp");
    try {
      value.setValue(true);
      configureFromText("a");
      initFind();

      myFindModel.setRegularExpressions(true);
      myFindModel.setStringToFind("a");
      myFindModel.setStringToReplace("");
      myFindModel.setReplaceState(true);

      myLivePreviewController.performReplace();
      checkResults();
    }
    finally {
      value.resetToDefault();
    }
  }

  public void testNoPreviewReplacementWithEmptyString() throws FindManager.MalformedReplacementStringException {
    RegistryValue value = Registry.get("ide.find.show.replacement.hint.for.simple.regexp");
    try {
      value.setValue(false);
      configureFromText("a");
      initFind();

      myFindModel.setRegularExpressions(true);
      myFindModel.setStringToFind("a");
      myFindModel.setStringToReplace("");
      myFindModel.setReplaceState(true);

      myLivePreviewController.performReplace();
      checkResults();
    }
    finally {
      value.resetToDefault();
    }
  }

  public void testSecondFind() {
    configureFromText("<selection>a<caret></selection> b b a");
    invokeFind();
    new EditorMouseFixture((EditorImpl)getEditor()).doubleClickAt(0, 3);
    invokeFind();
    checkResultByText("a <selection>b<caret></selection> b a");
  }

  public void testSecondRegexReplaceShowsPopup() throws FindManager.MalformedReplacementStringException {
    RegistryValue value = Registry.get("ide.find.show.replacement.hint.for.simple.regexp");
    try {
      value.setValue(true);
      configureFromText("<caret> aba");
      initFind();
      myFindModel.setRegularExpressions(true);
      myFindModel.setStringToFind("a");
      myFindModel.setStringToReplace("c");
      myFindModel.setReplaceState(true);
      myLivePreviewController.performReplace();
      checkResults();
    }
    finally {
      value.resetToDefault();
    }
  }

  public void testNoPreviewSecondRegexReplaceShowsPopup() throws FindManager.MalformedReplacementStringException {
    RegistryValue value = Registry.get("ide.find.show.replacement.hint.for.simple.regexp");
    try {
      value.setValue(false);
      configureFromText("<caret> aba");
      initFind();
      myFindModel.setRegularExpressions(true);
      myFindModel.setStringToFind("a");
      myFindModel.setStringToReplace("c");
      myFindModel.setReplaceState(true);
      myLivePreviewController.performReplace();
      checkResults();
    }
    finally {
      value.resetToDefault();
    }
  }

  public void testMalformedRegex() {
    configureFromText("<caret> aba");
    initFind();
    myFindModel.setRegularExpressions(true);
    myFindModel.setStringToFind("a");
    myFindModel.setStringToReplace("c$");
    myFindModel.setReplaceState(true);
    try {
      myLivePreviewController.performReplace();
      fail("There should be an exception, " + FindManager.MalformedReplacementStringException.class);
    }
    catch (FindManager.MalformedReplacementStringException e) {
      //ignore
    }
  }

  public void testUndoingReplaceBringsChangePlaceIntoView() {
    configureFromText("abc\n\n\n\n\nabc\n");
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 3);
    executeAction(IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION);

    EditorTestUtil.testUndoInEditor(getEditor(), () -> {
      initFind();
      myFindModel.setReplaceState(true);
      myFindModel.setGlobal(false);
      myFindModel.setStringToFind("abc");
      myFindModel.setStringToReplace("def");

      try {
        myLivePreviewController.performReplace();
        myLivePreviewController.performReplace();
      }
      catch (FindManager.MalformedReplacementStringException e) {
        fail(e.getMessage());
      }

      executeAction(IdeActions.ACTION_UNDO);
      executeAction(IdeActions.ACTION_UNDO);

      checkResultByText("abc\n\n\n\n\nabc\n");
      checkOffsetIsVisible(getEditor(), 0);
    });
  }

  private static void checkOffsetIsVisible(@NotNull Editor editor, int offset) {
    Point point = editor.offsetToXY(offset);
    assertTrue(editor.getScrollingModel().getVisibleAreaOnScrollingFinished().contains(point));
  }

  private void invokeFind() {
    executeAction(IdeActions.ACTION_FIND);
    UIUtil.dispatchAllInvocationEvents();
  }

  private void configureFromText(String text) {
    configureFromFileText("file.txt", text);
  }

  private void checkResults() {
    String name = getTestName(false);
    assertSameLinesWithFile(getTestDataPath() + "/find/findInEditor/" + name + ".gold", myOutputStream.toString());
  }


  public void testReplacePerformance() throws Exception {
    String aas = StringUtil.repeat("a", 100);
    String text = StringUtil.repeat(aas + "\n" + StringUtil.repeat("aaaaasdbbbbbbbbbbbbbbbbb\n", 100), 1000);
    String bbs = StringUtil.repeat("b", 100);
    String repl = StringUtil.replace(text, aas, bbs);
    Editor editor = configureFromFileTextWithoutPSI(text);
    LivePreview.ourTestOutput = null;

    try {
      initFind(editor);
      myFindModel.setReplaceState(true);
      myFindModel.setPromptOnReplace(false);

      PlatformTestUtil.startPerformanceTest("replace", 45000, ()->{
        for (int i=0; i<25; i++) {
          myFindModel.   setStringToFind(aas);
          myFindModel.setStringToReplace(bbs);
          FindUtil.replace(editor.getProject(), editor, 0, myFindModel);
          assertEquals(repl, editor.getDocument().getText());
          myFindModel.   setStringToFind(bbs);
          myFindModel.setStringToReplace(aas);
          FindUtil.replace(editor.getProject(), editor, 0, myFindModel);
          assertEquals(text, editor.getDocument().getText());
        }
      }).assertTiming();
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }
}
