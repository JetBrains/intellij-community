/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to initiate 3-way merge operations for multiple versions of content of a particular virtual file.
 */
public interface DiffRequestFactory {
  /**
   * Creates a request for a merge operation. To execute the request, obtain the diff tool instance by calling
   * {@link com.intellij.openapi.diff.DiffManager#getDiffTool()} and then call {@link DiffTool#show(DiffRequest)}.
   *
   *
   * @param leftText First of the changed versions of the content (to be displayed in the left pane).
   * @param rightText Second of the changed versions of the content (to be displayed in the right pane).
   * @param originalContent The version of the content before changes.
   * @param file The file which is being merged.
   * @param project The project in the context of which the operation is executed.
   * @param actionButtonPresentation Specifies the text of the OK/Apply button in the dialog and possibly an additional action which is
   *                                 executed when the button is pressed.
   * @return The merge operation request.
   */
  MergeRequest createMergeRequest(String leftText, String rightText, String originalContent, @NotNull VirtualFile file, Project project,
                                  ActionButtonPresentation actionButtonPresentation);
}
