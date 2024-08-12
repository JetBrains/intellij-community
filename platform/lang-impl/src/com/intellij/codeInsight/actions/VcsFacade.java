// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.model.ModelPatch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class VcsFacade {
  public static final Key<CharSequence> TEST_REVISION_CONTENT = Key.create("test.revision.content");
  protected static final Logger LOG = Logger.getInstance(VcsFacade.class);

  protected VcsFacade() {
  }

  public static @NotNull VcsFacade getInstance() {
    return ApplicationManager.getApplication().getService(VcsFacade.class);
  }

  /**
   * @see com.intellij.openapi.vcs.ProjectLevelVcsManager#hasActiveVcss()
   */
  public boolean hasActiveVcss(@NotNull Project project) {
    return false;
  }

  /**
   * @return whether a file has uncommitted changes.
   */
  public boolean hasChanges(@NotNull PsiFile file) {
    return false;
  }

  /**
   * @return whether a directory has files with uncommitted changes in it.
   * <p/>
   * WARNING: DELETED files are NOT included (ie: we can't reformat them, thus they are ignored).
   */
  public boolean hasChanges(@NotNull VirtualFile file, @NotNull Project project) {
    return false;
  }

  public boolean hasChanges(@NotNull PsiDirectory directory) {
    return hasChanges(directory.getVirtualFile(), directory.getProject());
  }

  public boolean hasChanges(VirtualFile @NotNull [] files, @NotNull Project project) {
    for (VirtualFile file : files) {
      if (hasChanges(file, project)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasChanges(@NotNull Module module) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    for (VirtualFile root : rootManager.getContentRoots()) {
      if (hasChanges(root, module.getProject())) {
        return true;
      }
    }
    return false;
  }

  public boolean hasChanges(final @NotNull Project project) {
    final Set<VirtualFile> directories = BaseProjectDirectories.getBaseDirectories(project);
    return ContainerUtil.exists(directories, it -> hasChanges(it, project));
  }

  public @NotNull Boolean isFileUnderVcs(@NotNull PsiFile psiFile) {
    return false;
  }

  /**
   * @return '.ignore' file names for known vcses.
   */
  public @NotNull Set<String> getVcsIgnoreFileNames(@NotNull Project project) {
    return Collections.emptySet();
  }

  /**
   * @return PsiFiles with uncommitted changes under specified directories.
   */
  public @NotNull List<PsiFile> getChangedFilesFromDirs(@NotNull Project project, @NotNull List<? extends PsiDirectory> dirs) {
    return Collections.emptyList();
  }

  public @NotNull List<TextRange> getChangedTextRanges(@NotNull Project project, @NotNull PsiFile file) {
    ChangedRangesInfo helper = getChangedRangesInfo(file);
    return helper != null ? helper.allChangedRanges : new ArrayList<>();
  }

  /**
   * @return a number of changed lines relative to given content. Deleted lines are included in the total number.
   */
  public int calculateChangedLinesNumber(@NotNull Document document, @NotNull CharSequence contentFromVcs) {
    return -1;
  }

  /**
   * @return whether the file is NOT tracked by vcs: either not under vcs root or is unversioned ({@link com.intellij.openapi.vcs.FileStatus#UNKNOWN}).
   */
  public boolean isChangeNotTrackedForFile(@NotNull Project project, @NotNull PsiFile file) {
    return true;
  }

  /**
   * @return the text ranges with uncommitted changes
   * <p/>
   * Deleted lines are ignored.
   * {@link ChangedRangesInfo#insertedRanges} contains 'completely new' lines.
   * {@link ChangedRangesInfo#insertedRanges} is {@code null} if the whole file is new.
   */
  public @Nullable ChangedRangesInfo getChangedRangesInfo(@NotNull PsiFile file) {
    return null;
  }

  /**
   * Notify VCS that local file content has changed in a way, that IDE might not detect, and uncommitted changes should be updated.
   *
   * @see com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
   */
  public void markFilesDirty(@NotNull Project project, @NotNull List<? extends VirtualFile> virtualFiles) {
  }

  /**
   * Allows to temporally suppress document modification tracking.
   * <p/>
   * Ex: To perform a task, that might delete a whole document and re-create it from scratch (ex: rearrange methods by re-inserting).
   * Such modification would destroy all existing modified line ranges and associated data, unless handled as atomic change.
   * <p/>
   * While using `runHeavyModificationTask` would make trackers compare only the starting and finishing document states,
   * ignoring intermediate modifications (assuming that "cumulative" differences will be more incremental).
   *
   * @see com.intellij.openapi.vcs.ex.LineStatusTracker
   * @see com.intellij.util.DocumentUtil#executeInBulk
   */
  public void runHeavyModificationTask(@NotNull Project project, @NotNull Document document, @NotNull Runnable o) {
    o.run();
  }

  @ApiStatus.Experimental
  public @Nullable JComponent createPatchPreviewComponent(@NotNull Project project, @NotNull ModelPatch patch) {
    return null;
  }
}
