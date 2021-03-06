// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import org.jetbrains.annotations.NonNls;

/**
 * @see DiffUserDataKeys#PLACE
 */
public interface DiffPlaces {
  @NonNls String DEFAULT = "Default";
  @NonNls String CHANGES_VIEW = "ChangesView";
  @NonNls String VCS_LOG_VIEW = "VcsLogView";
  @NonNls String VCS_FILE_HISTORY_VIEW = "VcsFileHistoryView";
  @NonNls String COMMIT_DIALOG = "CommitDialog";
  @NonNls String TESTS_FAILED_ASSERTIONS = "TestsFiledAssertions";
  @NonNls String MERGE = "Merge";
  @NonNls String DIR_DIFF = "DirDiff";
  @NonNls String EXTERNAL = "External";
}
