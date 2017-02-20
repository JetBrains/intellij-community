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
 * Upcoming push information holder for one repository
 */
public interface PushInfo {
  /**
   * Repository of the push source
   */
  @NotNull
  Repository getRepository();

  /**
   * Specifies what would be pushed and where for this repository
   *
   * @return push specification
   */
  @NotNull
  PushSpec<PushSource, PushTarget> getPushSpec();

  /**
   * Returns list of commits to be pushed.
   * Commits are ordered so that the most recent come last, e.g. as in the output of git log source..target but in reverse order
   */
  @NotNull
  List<VcsFullCommitDetails> getCommits();
}
