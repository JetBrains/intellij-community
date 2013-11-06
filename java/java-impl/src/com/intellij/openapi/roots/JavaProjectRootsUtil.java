package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JavaProjectRootsUtil {
  public static boolean isOutsideJavaSourceRoot(@Nullable PsiFile psiFile) {
    if (psiFile == null) return false;
    if (psiFile instanceof PsiCodeFragment) return false;
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return false;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();
    return !projectFileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) && !projectFileIndex.isInLibrarySource(file)
           && !projectFileIndex.isInLibraryClasses(file);
  }

  /**
   * @return list of all java source roots in the project which can be suggested as a target directory for a class created by user
   */
  @NotNull
  public static List<VirtualFile> getSuitableDestinationSourceRoots(@NotNull Project project) {
    List<VirtualFile> roots = new ArrayList<VirtualFile>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
        for (SourceFolder sourceFolder : entry.getSourceFolders(JavaModuleSourceRootTypes.SOURCES)) {
          JavaSourceRootProperties properties = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
          if (properties != null && !properties.isForGeneratedSources()) {
            ContainerUtil.addIfNotNull(roots, sourceFolder.getFile());
          }
        }
      }
    }
    return roots;
  }
}
