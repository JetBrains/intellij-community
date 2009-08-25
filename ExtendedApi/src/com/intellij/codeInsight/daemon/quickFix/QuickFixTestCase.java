package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * @author Maxim.Mossienko
*         Date: 22.01.2009
*         Time: 0:28:45
*/
public interface QuickFixTestCase {
  String getBasePath();

  String getTestDataPath();

  Pair<String, Boolean> parseActionHintImpl(PsiFile file, String contents);

  void beforeActionStarted(String testName, String contents);

  void afterActionCompleted(String testName, String contents);

  void doAction(String text, boolean actionShouldBeAvailable, String testFullPath, String testName) throws Exception;

  void checkResultByFile(String s, String expectedFilePath, boolean b) throws Exception;

  IntentionAction findActionWithText(String text);

  boolean shouldBeAvailableAfterExecution();

  void invoke(IntentionAction action);

  List<HighlightInfo> doHighlighting();

  List<IntentionAction> getAvailableActions();

  void bringRealEditorBack();

  void configureFromFileText(String name, String contents) throws Throwable;

  PsiFile getFile();
}
