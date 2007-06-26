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
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Interface for performing VCS checkin / commit / submit operations.
 *
 * @author lesya
 * @see com.intellij.openapi.vcs.AbstractVcs#getCheckinEnvironment()
 */
public interface CheckinEnvironment {
  @Nullable
  RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel);

  @Nullable
  String getDefaultMessageFor(FilePath[] filesToCheckin);

  String prepareCheckinMessage(String text);

  @Nullable
  @NonNls
  String getHelpId();

  String getCheckinOperationName();

  /**
   * @return true if check in dialog should be shown even if there are no files to check in
   */
  boolean showCheckinDialogInAnyCase();

  List<VcsException> commit(List<Change> changes, String preparedComment);
  List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files);
  List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files);
}
