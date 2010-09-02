/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class HighlightLevelUtil {
  private HighlightLevelUtil() {
  }
  public enum AnalysisLevel {
    HIGHLIGHT, HIGHLIGHT_AND_INSPECT
  }
  public static boolean shouldAnalyse(@NotNull PsiFile root, @NotNull AnalysisLevel analysisLevel) {
    return analysisLevel == AnalysisLevel.HIGHLIGHT_AND_INSPECT ? shouldInspect(root) : shouldHighlight(root);
  }


  public static boolean shouldHighlight(@NotNull PsiElement psiRoot) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(psiRoot.getProject());
    if (component == null) return true;

    final FileHighlighingSetting settingForRoot = component.getHighlightingSettingForRoot(psiRoot);
    return settingForRoot != FileHighlighingSetting.SKIP_HIGHLIGHTING;
  }

  public static boolean shouldInspect(@NotNull PsiElement psiRoot) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;

    if (!shouldHighlight(psiRoot)) return false;
    final Project project = psiRoot.getProject();
    final VirtualFile virtualFile = psiRoot.getContainingFile().getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return false;

    if (ProjectUtil.isProjectOrWorkspaceFile(virtualFile)) return false;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if ((fileIndex.isInLibrarySource(virtualFile) || fileIndex.isInLibraryClasses(virtualFile)) && !fileIndex.isInContent(virtualFile)) {
      return false;
    }
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(project);
    if (component == null) return true;

    final FileHighlighingSetting settingForRoot = component.getHighlightingSettingForRoot(psiRoot);
    return settingForRoot != FileHighlighingSetting.SKIP_INSPECTION;
  }

  public static void forceRootHighlighting(@NotNull PsiElement root, @NotNull FileHighlighingSetting level) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(root.getProject());
    if (component == null) return;

    component.setHighlightingSettingForRoot(root, level);
  }
}
