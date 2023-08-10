// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.IntentionActionFilter;
import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * We want to highlight some files, that do not belong to the project (ex: previous revisions from VCS).
 * To do so we need some context - SDK, the rest of the project, etc.
 * We can't get "real" context, but local state is often a good enough approximation.
 * <p>
 * This helper is used to disable error highlighting and inspections in such files,
 * as they tend to be false-positive due to context differences.
 */
public final class OutsidersPsiFileSupport {
  private static final Key<Boolean> KEY = Key.create("OutsidersPsiFileSupport");
  private static final Key<String> VFS_URL_KEY = Key.create("OutsidersPsiFileSupport.FilePath");

  public static class HighlightFilter implements HighlightInfoFilter {
    @Override
    public boolean accept(@NotNull HighlightInfo info, @Nullable PsiFile file) {
      if (!isOutsiderFile(file)) return true;
      if (info.getSeverity() == HighlightSeverity.ERROR) return false;
      return true;
    }
  }

  public static class IntentionFilter implements IntentionActionFilter {
    @Override
    public boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile file) {
      return !isOutsiderFile(file);
    }
  }

  public static class HighlightingSettingProvider extends DefaultHighlightingSettingProvider {
    @Nullable
    @Override
    public FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file) {
      if (!isOutsiderFile(file)) return null;
      return FileHighlightingSetting.SKIP_INSPECTION;
    }
  }


  public static void markFile(@NotNull VirtualFile file) {
    markFileWithUrl(file, null);
  }

  public static void markFile(@NotNull VirtualFile file, @Nullable String originalPath) {
    var vfsUrl = originalPath != null ? VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, originalPath) : null;
    markFileWithUrl(file, vfsUrl);
  }

  /**
   * @param originalVfsUrl The VFS URL of the outsider file's original file as defined by {@link VirtualFile#getUrl}, or `null` if the
   *                       outsider doesn't have an original file.
   */
  public static void markFileWithUrl(@NotNull VirtualFile file, @Nullable String originalVfsUrl) {
    file.putUserData(KEY, Boolean.TRUE);
    if (originalVfsUrl != null) file.putUserData(VFS_URL_KEY, FileUtil.toSystemIndependentName(originalVfsUrl));
  }

  public static boolean isOutsiderFile(@Nullable PsiFile file) {
    return file != null && isOutsiderFile(file.getVirtualFile());
  }

  public static boolean isOutsiderFile(@Nullable VirtualFile file) {
    return file != null && file.getUserData(KEY) == Boolean.TRUE;
  }

  /**
   * @return The VFS URL of the outsider file's original file as defined by {@link VirtualFile#getUrl}, or `null` if the outsider doesn't
   *         have an original file.
   */
  @Nullable
  public static String getOriginalFileUrl(@NotNull VirtualFile file) {
    return file.getUserData(VFS_URL_KEY);
  }

  @Nullable
  public static String getOriginalFilePath(@NotNull VirtualFile file) {
    var vfsUrl = getOriginalFileUrl(file);
    return vfsUrl != null ? VirtualFileManager.extractPath(vfsUrl) : null;
  }
}
