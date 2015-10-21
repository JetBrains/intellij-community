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
package com.intellij.openapi.diff;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * A request for a merge operation.
 *
 * @see DiffRequestFactory#createMergeRequest
 * @deprecated use {@link com.intellij.diff.merge.MergeRequest} instead
 */
@Deprecated
public abstract class MergeRequest extends DiffRequest {
  protected MergeRequest(@Nullable Project project) {
    super(project);
  }

  /**
   * Sets the titles of panes in the merge dialog.
   *
   * @param versionTitles Array of 3 strings. First string specifies left pane title, second string specifies middle pane (merged content)
   * title, third string specifies right pane.
   */
  public abstract void setVersionTitles(String[] versionTitles);

  /**
   * Sets the title of the merge dialog.
   *
   * @param windowTitle The dialog title.
   */
  public abstract void setWindowTitle(String windowTitle);

  /**
   * Specifies the ID of the help topic which is shown when the Help button is pressed. If null, the Help button is not shown.
   *
   * @param helpId the ID of the help topic for the merge operation.
   */
  public abstract void setHelpId(@Nullable @NonNls String helpId);

  /**
   * After the merge operation is completed, returns the exit code of the merge dialog.
   *
   * @return {@link com.intellij.openapi.ui.DialogWrapper#OK_EXIT_CODE} if the user accepted the merge, or a different value if the merge
   * operation was cancelled.
   */
  public abstract int getResult();

  /**
   * After the merge operation is completed, returns the merged text.
   *
   * @return the merged text.
   */
  @Nullable
  public abstract DiffContent getResultContent();

  public abstract void restoreOriginalContent();
}
