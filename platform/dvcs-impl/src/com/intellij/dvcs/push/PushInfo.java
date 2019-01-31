/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.dvcs.push;

import com.intellij.dvcs.repo.Repository;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Push information for one repository, available from the push dialog.
 */
public interface PushInfo {
  /**
   * Repository of the push source
   */
  @NotNull
  Repository getRepository();

  /**
   * Specifies what would be pushed and where for this repository
   */
  @NotNull
  PushSpec<PushSource, PushTarget> getPushSpec();

  /**
   * Returns list of commits to be pushed.
   * Commits are ordered so that the most recent come last, e.g. as in the output of git log source..target but in reverse order
   * <br/><br/>
   * <b>NB: </b> The list of commits reflects the state of the push dialog, it can be empty, if the push info is requested before
   * the outgoing list of commits is populated.
   */
  @NotNull
  List<VcsFullCommitDetails> getCommits();
}
