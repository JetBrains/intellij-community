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
package com.intellij.dvcs.cherrypick;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public abstract class VcsCherryPicker {

  @NonNls public static final ExtensionPointName<VcsCherryPicker> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.cherryPicker");
  /**
   * @return - return vcs for current cherryPicker
   */
  @NotNull
  public abstract VcsKey getSupportedVcs();

  /**
   * @return CherryPick Action name for supported vcs
   */
  @NotNull
  public abstract String getActionTitle();

  /**
   * Cherry-pick selected commits to current branch of appropriate repository
   *
   * @param commits to cherry-pick
   */
  public abstract void cherryPick(@NotNull final List<VcsFullCommitDetails> commits);

  /**
   * Return true if cherry picker can manage all commits from roots
   */
  public abstract boolean canHandleForRoots(@NotNull Collection<VirtualFile> roots);
}
