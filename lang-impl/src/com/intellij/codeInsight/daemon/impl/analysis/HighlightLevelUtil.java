package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

public class HighlightLevelUtil {
  private HighlightLevelUtil() {
  }

  public static boolean shouldHighlight(final PsiElement psiRoot) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(psiRoot.getProject());
    if (component == null) return true;

    final FileHighlighingSetting settingForRoot = component.getHighlightingSettingForRoot(psiRoot);
    return settingForRoot != FileHighlighingSetting.SKIP_HIGHLIGHTING;
  }

  public static boolean shouldInspect(final PsiElement psiRoot) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;

    if (!shouldHighlight(psiRoot)) return false;
    final Project project = psiRoot.getProject();
    final VirtualFile virtualFile = psiRoot.getContainingFile().getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return false;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if ((fileIndex.isInLibrarySource(virtualFile) || fileIndex.isInLibraryClasses(virtualFile)) && !fileIndex.isInContent(virtualFile)) {
      return false;
    }
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(project);
    if (component == null) return true;

    final FileHighlighingSetting settingForRoot = component.getHighlightingSettingForRoot(psiRoot);
    return settingForRoot != FileHighlighingSetting.SKIP_INSPECTION;
  }

  public static void forceRootHighlighting(final PsiElement root, FileHighlighingSetting level) {
    final HighlightingSettingsPerFile component = HighlightingSettingsPerFile.getInstance(root.getProject());
    if (component == null) return;

    component.setHighlightingSettingForRoot(root, level);
  }
}
