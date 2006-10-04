/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 *
 * @see IdeaTestFixtureFactory#createCodeInsightFixture(IdeaProjectTestFixture)
 *
 * @author Dmitry Avdeev
 */
public interface CodeInsightTestFixture extends IdeaTestFixture {

  @NonNls String CARET_MARKER = "<caret>";
  @NonNls String SELECTION_START_MARKER = "<selection>";
  @NonNls String SELECTION_END_MARKER = "</selection>";

  @NonNls String ERROR_MARKER = "error";
  @NonNls String WARNING_MARKER = "warning";
  @NonNls String INFORMATION_MARKER = "weak_warning";
  @NonNls String SERVER_PROBLEM_MARKER = "server_problem";
  @NonNls String INFO_MARKER = "info";
  @NonNls String END_LINE_HIGHLIGHT_MARKER = "EOLError";
  @NonNls String END_LINE_WARNING_MARKER = "EOLWarning";

  Project getProject();

  Editor getEditor();
  
  PsiFile getFile();

  void setTestDataPath(String dataPath);

  String getTempDirPath();

  /**
   * Enables inspections for highlighting tests.
   * Should be called BEFORE {@link #setUp()} 
   *
   * @param inspections inspections to be enabled in highliting tests
   */
  void enableInspections(LocalInspectionTool... inspections);

  /**
   * Runs highliting test for the given files
   * Checks for {@link #ERROR_MARKER} markers by default
   *
   * @param checkWarnings enables {@link #WARNING_MARKER} support
   * @param checkInfos enables {@link #INFO_MARKER} support
   * @param checkWeakWarnings enables {@link #INFORMATION_MARKER} support
   * @param filePaths the first file is tested only; the others are just copied along the first
   *
   * @return highlighting duration in milliseconds
   * @throws Throwable any exception thrown during highlighting
   */
  long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, String... filePaths) throws Throwable;

  /**
   * Runs highliting test for the given files.
   * The same as {@link #testHighlighting(boolean, boolean, boolean, String...)} with all options set.
   *
   * @param filePaths the first file is tested only; the others are just copied along the first
   *
   * @return highlighting duration in milliseconds
   * @throws Throwable any exception thrown during highlighting
   */
  long testHighlighting(String... filePaths) throws Throwable;

  @NotNull
  Collection<IntentionAction> getAvailableIntentions(String... filePaths) throws Throwable;

  void launchAction(IntentionAction action) throws Throwable;

  void testCompletion(String[] filesBefore, String fileAfter) throws Throwable;
}
