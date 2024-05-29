// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.cherrypick;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsCommitMetadata;
import org.jetbrains.annotations.Nls;
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
  @Nls(capitalization = Nls.Capitalization.Title)
  public abstract String getActionTitle();

  /**
   * Cherry-pick selected commits to current branch of appropriate repository
   *
   * @param commits to cherry-pick
   * @return true if cherry-pick was successful
   */
  public abstract boolean cherryPick(@NotNull final List<? extends VcsCommitMetadata> commits);

  /**
   * Return true if cherry picker can manage all commits from roots
   */
  public abstract boolean canHandleForRoots(@NotNull Collection<? extends VirtualFile> roots);
}
