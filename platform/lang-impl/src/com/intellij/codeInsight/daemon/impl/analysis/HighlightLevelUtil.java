package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class HighlightLevelUtil {
  private HighlightLevelUtil() {
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

    final FileType fileType = virtualFile.getFileType();
    if (fileType == StdFileTypes.IDEA_MODULE || fileType == StdFileTypes.IDEA_PROJECT || fileType == StdFileTypes.IDEA_WORKSPACE) return false;

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
