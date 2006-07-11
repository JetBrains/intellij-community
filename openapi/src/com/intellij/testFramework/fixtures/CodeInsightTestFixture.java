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

import org.jetbrains.annotations.NonNls;

/**
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

  void setTestDataPath(String dataPath);

  String getTempDirPath();

  void testHighlighting(String filePath, boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings);

  /**
   * Runs highliting test for the given file
   * @param filePath path relative to the one set by {@link #setTestDataPath(String)}
   */
  void testHighlighting(String filePath);
}
