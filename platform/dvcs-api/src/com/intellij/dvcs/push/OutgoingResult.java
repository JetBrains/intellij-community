/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OutgoingResult {
  @NotNull private final List<VcsError> myErrors;
  @NotNull private final List<? extends VcsFullCommitDetails> myCommits;

  public OutgoingResult(@NotNull List<? extends VcsFullCommitDetails> commits, @NotNull List<VcsError> errors) {
    myCommits = commits;
    myErrors = errors;
  }

  @NotNull
  public List<VcsError> getErrors() {
    return myErrors;
  }

  @NotNull
  public List<? extends VcsFullCommitDetails> getCommits() {
    return myCommits;
  }
}
