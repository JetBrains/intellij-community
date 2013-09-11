package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

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
}
