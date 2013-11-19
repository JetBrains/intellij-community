package com.intellij.find;

import com.intellij.testFramework.LightVirtualFile;
import junit.framework.Assert;

public class FindManagerTestUtils {
  static void runFindInCommentsAndLiterals(FindManager findManager, FindModel findModel, String text) {
    runFindInCommentsAndLiterals(findManager, findModel, text, "java");
  }

  static void runFindInCommentsAndLiterals(FindManager findManager,
                                           FindModel findModel,
                                           String text,
                                           String ext) {
    findModel.setInStringLiteralsOnly(true);
    findModel.setInCommentsOnly(false);
    runFindForwardAndBackward(findManager, findModel, text, ext);

    findModel.setInStringLiteralsOnly(false);
    findModel.setInCommentsOnly(true);
    runFindForwardAndBackward(findManager, findModel, text, ext);
  }

  static void runFindForwardAndBackward(FindManager findManager, FindModel findModel, String text) {
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
