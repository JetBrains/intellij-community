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

  public static final @NonNls ExtensionPointName<VcsCherryPicker> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.cherryPicker");
  /**
   * @return - return vcs for current cherryPicker
   */
  public abstract @NotNull VcsKey getSupportedVcs();

  /**
   * @return CherryPick Action name for supported vcs
   */
  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getActionTitle();

  /**
   * Cherry-pick selected commits to current branch of appropriate repository
   *
   * @param commits to cherry-pick
   * @return true if cherry-pick was successful
   */
  public abstract boolean cherryPick(final @NotNull List<? extends VcsCommitMetadata> commits);

  /**
   * Return true if cherry picker can manage all commits from roots
   */
  public abstract boolean canHandleForRoots(@NotNull Collection<? extends VirtualFile> roots);
}
