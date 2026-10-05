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

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Source to push from. For example, local branch for git or branch/bookmark for mercurial.
 */
public interface PushSource {
  @Nls
  @NotNull
  String getPresentation();

  /**
   * Returns the name of the local branch that is pushed.
   * Returns {@code null} when the source is not a branch, for example a revision or a detached HEAD.
   */
  @ApiStatus.Experimental
  default @NlsSafe @Nullable String getBranchName() {
    return null;
  }
}
