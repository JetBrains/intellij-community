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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
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
public class OutsidersPsiFileSupport {
  public static final Key<Boolean> KEY = Key.create("OutsidersPsiFileSupport");

  public static class HighlightFilter implements HighlightInfoFilter {
    @Override
    public boolean accept(@NotNull HighlightInfo info, @Nullable PsiFile file) {
      if (!isDiffFile(file)) return true;
      if (info.getSeverity() == HighlightSeverity.ERROR) return false;
      return true;
    }
  }

  public static class IntentionFilter implements IntentionActionFilter {
    @Override
    public boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile file) {
      return !isDiffFile(file);
    }
  }

  public static class HighlightingSettingProvider extends DefaultHighlightingSettingProvider {
    @Nullable
    @Override
    public FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file) {
      if (!isDiffFile(file)) return null;
      return FileHighlightingSetting.SKIP_INSPECTION;
    }
  }


  public static void markFile(@NotNull VirtualFile file) {
    file.putUserData(KEY, Boolean.TRUE);
  }

  public static boolean isDiffFile(@Nullable PsiFile file) {
    return file != null && isDiffFile(file.getVirtualFile());
  }

  public static boolean isDiffFile(@Nullable VirtualFile file) {
    return file != null && file.getUserData(KEY) == Boolean.TRUE;
  }
}
