/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.find;

import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.impl.livePreview.LivePreview;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class FindInEditorTest extends LightCodeInsightTestCase {
  private LivePreviewController myLivePreviewController;
  private FindModel myFindModel;

  private ByteArrayOutputStream myOutputStream;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFindModel = new FindModel();

    myOutputStream = new ByteArrayOutputStream();
    LivePreview.ourTestOutput = new PrintStream(myOutputStream);
    EditorHintListener listener = (project, hint, flags) -> LivePreview.processNotFound();
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
  // disabling execution of tests in command/write action
  protected void runTest() throws Throwable {
    doRunTest();
  }

  private void initFind() {
    SearchResults searchResults = new SearchResults(getEditor(), getProject());
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
    assertFalse(myEditor.getSelectionModel().hasSelection());
  }

  public void testEmacsLikeFallback() {
    configureFromText("a\nab");
    initFind();
    myFindModel.setStringToFind("a");
    myFindModel.setStringToFind("ab");
    myFindModel.setStringToFind("a");
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
    new EditorMouseFixture((EditorImpl)myEditor).doubleClickAt(0, 3);
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
    EditorTestUtil.setEditorVisibleSize(myEditor, 100, 3);
    executeAction(IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION);

    EditorTestUtil.testUndoInEditor(myEditor, () -> {
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
      checkOffsetIsVisible(myEditor, 0);
    });
  }

  private static void checkOffsetIsVisible(@NotNull Editor editor, int offset) {
    Point point = editor.offsetToXY(offset);
    assertTrue(editor.getScrollingModel().getVisibleAreaOnScrollingFinished().contains(point));
  }

  private static void invokeFind() {
    executeAction(IdeActions.ACTION_FIND);
    UIUtil.dispatchAllInvocationEvents();
  }

  private static void configureFromText(String text) {
    configureFromFileText("file.txt", text);
  }

  private void checkResults() {
    String name = getTestName(false);
    assertSameLinesWithFile(getTestDataPath() + "/find/findInEditor/" + name + ".gold", myOutputStream.toString());
  }
}
