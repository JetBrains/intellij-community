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

import com.intellij.dvcs.repo.Repository;
import org.jetbrains.annotations.NotNull;

/**
 * Provider for outgoing commits
 */
public abstract class OutgoingCommitsProvider {

  /**
   * Collect outgoing commits or errors for selected  repo for specified {@link PushSpec}   and store to {@link OutgoingResult}
   *
   * @param initial    true for first commits loading, which identify that all inside actions should be silent
   *                   and do not ask user about smth, a.e authorization request
   */
  @NotNull
  public abstract OutgoingResult getOutgoingCommits(@NotNull Repository repository,
                                                    @NotNull PushSpec pushSpec, boolean initial);
}
