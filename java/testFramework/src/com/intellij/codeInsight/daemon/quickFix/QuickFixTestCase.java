/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Maxim.Mossienko
*/
public interface QuickFixTestCase {
  String getBasePath();

  @NotNull
  String getTestDataPath();

  @NotNull
  ActionHint parseActionHintImpl(@NotNull PsiFile file, @NotNull String contents);

  void beforeActionStarted(@NotNull String testName, @NotNull String contents);

  void afterActionCompleted(@NotNull String testName, @NotNull String contents);

  void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) throws Exception;

  void checkResultByFile(@NotNull String message, @NotNull String expectedFilePath, boolean ignoreTrailingSpaces) throws Exception;

  IntentionAction findActionWithText(@NotNull String text);

  boolean shouldBeAvailableAfterExecution();

  void invoke(@NotNull IntentionAction action);

  @NotNull
  List<HighlightInfo> doHighlighting();

  @NotNull
  List<IntentionAction> getAvailableActions();

  void bringRealEditorBack();

  void configureFromFileText(@NotNull String name, @NotNull String contents) throws Throwable;

  PsiFile getFile();

  Project getProject();
}
