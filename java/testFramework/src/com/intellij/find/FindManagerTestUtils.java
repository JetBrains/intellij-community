/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.testFramework.LightVirtualFile;
import org.junit.Assert;

public class FindManagerTestUtils {
  static void runFindInCommentsAndLiterals(FindManager findManager, FindModel findModel, String text) {
    runFindInCommentsAndLiterals(findManager, findModel, text, "java");
  }

  public static void runFindInCommentsAndLiterals(FindManager findManager, FindModel findModel, String text, String ext) {
    findModel.setSearchContext(FindModel.SearchContext.IN_STRING_LITERALS);
    runFindForwardAndBackward(findManager, findModel, text, ext);

    findModel.setSearchContext(FindModel.SearchContext.IN_COMMENTS);
    runFindForwardAndBackward(findManager, findModel, text, ext);
  }

  public static void runFindForwardAndBackward(FindManager findManager, FindModel findModel, String text) {
    runFindForwardAndBackward(findManager, findModel, text, "java");
  }

  public static void runFindForwardAndBackward(FindManager findManager, FindModel findModel, String text, String ext) {
    findModel.setForward(true);
    LightVirtualFile file = new LightVirtualFile("A."+ext, text);
    int previousOffset;

    FindResult findResult = findManager.findString(text, 0, findModel, file);
    Assert.assertTrue(findResult.isStringFound());
    previousOffset = findResult.getStartOffset();

    findResult = findManager.findString(text, findResult.getEndOffset(), findModel, file);
    Assert.assertTrue(findResult.isStringFound());
    Assert.assertTrue(findResult.getStartOffset() > previousOffset);
    previousOffset = findResult.getStartOffset();

    findResult = findManager.findString(text, findResult.getEndOffset(), findModel, file);
    Assert.assertTrue(findResult.isStringFound());
    Assert.assertTrue(findResult.getStartOffset() > previousOffset);

    findModel.setForward(false);

    findResult = findManager.findString(text, text.length(), findModel, file);
    Assert.assertTrue(findResult.isStringFound());
    previousOffset = findResult.getStartOffset();

    findResult = findManager.findString(text, previousOffset, findModel, file);
    Assert.assertTrue(findResult.isStringFound());
    Assert.assertTrue(previousOffset > findResult.getStartOffset());

    previousOffset = findResult.getStartOffset();

    findResult = findManager.findString(text, previousOffset, findModel, file);
    Assert.assertTrue(findResult.isStringFound());
    Assert.assertTrue(previousOffset > findResult.getStartOffset());
  }

  public static FindModel configureFindModel(String stringToFind) {
    FindModel findModel = new FindModel();
    findModel.setStringToFind(stringToFind);
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    return findModel;
  }
}
