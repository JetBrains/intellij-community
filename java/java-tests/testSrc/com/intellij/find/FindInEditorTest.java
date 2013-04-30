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

import com.intellij.find.impl.FindResultImpl;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.util.List;

public class FindInEditorTest extends LightCodeInsightTestCase {

  private LivePreviewController myLivePreviewController;
  private SearchResults mySearchResults;
  private FindModel myFindModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFindModel = new FindModel();
    myFindModel.addObserver(new FindModel.FindModelObserver() {
      @Override
      public void findModelChanged(FindModel findModel) {
        myLivePreviewController.updateInBackground(myFindModel, true);
      }
    });
  }

  private void initFind() {
    mySearchResults = new SearchResults(getEditor(), getProject());
    myLivePreviewController = new LivePreviewController(mySearchResults, null);
    myLivePreviewController.on();
  }

  public void test1() throws Exception {
    configureFromFileText("file.txt", "abc");
    initFind();
    myFindModel.setStringToFind("a");
    type("a");
    List<FindResult> occurrences = mySearchResults.getOccurrences();
    assertContainsElements(occurrences, new FindResultImpl(0, 1));
  }
}
