/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.Project;
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

  Project getProject();
}
