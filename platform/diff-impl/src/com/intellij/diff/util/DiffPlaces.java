/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.util;

import org.jetbrains.annotations.NonNls;

public interface DiffPlaces {
  @NonNls String DEFAULT = "Default";
  @NonNls String CHANGES_VIEW = "ChangesView";
  @NonNls String VCS_LOG_VIEW = "VcsLogView";
  @NonNls String COMMIT_DIALOG = "CommitDialog";
  @NonNls String TESTS_FAILED_ASSERTIONS = "TestsFiledAssertions";
  @NonNls String MERGE = "Merge";
  @NonNls String DIR_DIFF = "DirDiff";
  @NonNls String EXTERNAL = "External";
}
