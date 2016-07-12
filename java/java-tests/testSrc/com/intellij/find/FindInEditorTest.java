/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.find;

import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.find.impl.livePreview.LivePreview;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.util.ui.UIUtil;

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
    ApplicationManager.getApplication().getMessageBus().connect(myTestRootDisposable).subscribe(EditorHintListener.TOPIC, listener);
  }

  private void initFind() {
    SearchResults searchResults = new SearchResults(getEditor(), getProject());
    myLivePreviewController = new LivePreviewController(searchResults, null);
    myFindModel.addObserver(findModel -> myLivePreviewController.updateInBackground(myFindModel, true));
    myLivePreviewController.on();
  }

  public void testBasicFind() throws Exception {
    configureFromText("ab");
    initFind();
    myFindModel.setStringToFind("a");
    checkResults();
  }

  public void testEmacsLikeFallback() throws Exception {
    configureFromText("a\nab");
    initFind();
    myFindModel.setStringToFind("a");
    myFindModel.setStringToFind("ab");
    myFindModel.setStringToFind("a");
    checkResults();
  }

  public void testReplacementWithEmptyString() throws Exception {
    configureFromText("a");
    initFind();

    myFindModel.setRegularExpressions(true);
    myFindModel.setStringToFind("a");
    myFindModel.setStringToReplace("");
    myFindModel.setReplaceState(true);

    myLivePreviewController.performReplace();
    checkResults();
  }
  
  public void testSecondFind() throws Exception {
    configureFromText("<selection>a<caret></selection> b b a");
    invokeFind();
    new EditorMouseFixture((EditorImpl)myEditor).doubleClickAt(0, 3);
    invokeFind();
    checkResultByText("a <selection>b<caret></selection> b a");
  }

  public void testSecondRegexReplaceShowsPopup() throws Exception {
    configureFromText("<caret> aba");
    initFind();
    myFindModel.setRegularExpressions(true);
    myFindModel.setStringToFind("a");
    myFindModel.setStringToReplace("c");
    myFindModel.setReplaceState(true);
    myLivePreviewController.performReplace();
    checkResults();
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
