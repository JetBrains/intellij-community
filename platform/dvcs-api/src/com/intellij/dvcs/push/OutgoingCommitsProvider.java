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
public abstract class OutgoingCommitsProvider<Repo extends Repository, Source extends PushSource, Target extends PushTarget>  {

  /**
   * Collect either outgoing commits or errors for the given repository and {@link PushSpec}.
   *
   * @param initial true for the first attempt to load commits, which happens when the push dialog just appears on the screen.
   *                If later the user modifies the push target, commits are reloaded, and {@code initial} is false.
   *                <br/>
   *                Implementations should make sure that if {@code initial} is true, no user interaction is allowed
   *                (to avoid suddenly throwing dialogs into user's face).
   *                E.g. if authentication is needed to collect outgoing changes, then the method should silently show the corresponding
   *                request in the error field of the OutgoingResult.
   */
  @NotNull
  public abstract OutgoingResult getOutgoingCommits(@NotNull Repo repository, @NotNull PushSpec<Source, Target> pushSpec, boolean initial);

}
