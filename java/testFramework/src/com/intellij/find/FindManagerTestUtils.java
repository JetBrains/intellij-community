// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find;

import com.intellij.testFramework.LightVirtualFile;
import org.junit.Assert;

public final class FindManagerTestUtils {
  public static void runFindInCommentsAndLiterals(FindManager findManager, FindModel findModel, String text) {
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

    FindResult findResult = findManager.findString(text, 0, findModel, file);
    Assert.assertTrue(findResult.isStringFound());
    int previousOffset = findResult.getStartOffset();

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
