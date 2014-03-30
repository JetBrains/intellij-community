/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.find.impl.livePreview.LivePreview;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class FindInEditorTest extends LightCodeInsightTestCase {

  private LivePreviewController myLivePreviewController;
  private SearchResults mySearchResults;
  private FindModel myFindModel;

  private ByteArrayOutputStream myOutputStream;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFindModel = new FindModel();

    myOutputStream = new ByteArrayOutputStream();
    LivePreview.ourTestOutput = new PrintStream(myOutputStream);
  }

  private void initFind() {
    mySearchResults = new SearchResults(getEditor(), getProject());
    myLivePreviewController = new LivePreviewController(mySearchResults, null);
    myFindModel.addObserver(new FindModel.FindModelObserver() {
      @Override
      public void findModelChanged(FindModel findModel) {
        myLivePreviewController.updateInBackground(myFindModel, true);
      }
    });
    myLivePreviewController.on();
  }

  public void testBasicFind() throws Exception {
    configureFromFileText("file.txt", "ab");
    initFind();
    myFindModel.setStringToFind("a");
    checkResults();
  }

  public void testEmacsLikeFallback() throws Exception {
    configureFromFileText("file.txt", "a\nab");
    initFind();
    myFindModel.setStringToFind("a");
    myFindModel.setStringToFind("ab");
    myFindModel.setStringToFind("a");
    checkResults();
  }

  public void testReplacementWithEmptyString() throws Exception {
    configureFromFileText("file.txt", "a");
    initFind();

    myFindModel.setRegularExpressions(true);
    myFindModel.setStringToFind("a");
    myFindModel.setStringToReplace("");
    myFindModel.setReplaceState(true);

    myLivePreviewController.performReplace();
    checkResults();
  }

  private void checkResults() {
    String name = getTestName(false);
    assertSameLinesWithFile(getTestDataPath() + "/find/findInEditor/" + name + ".gold", myOutputStream.toString());
  }
}
