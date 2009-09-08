package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public abstract class NonClasspathClassFinder extends PsiElementFinder {
  protected final Project myProject;

  public NonClasspathClassFinder(Project project) {
    myProject = project;
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return null;
    }

    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final VirtualFile classFile = classRoot.findFileByRelativePath(qualifiedName.replace('.', '/') + ".class");
        if (classFile != null) {
          final PsiFile file = PsiManager.getInstance(myProject).findFile(classFile);
          if (file instanceof PsiClassOwner) {
            final PsiClass[] classes = ((PsiClassOwner)file).getClasses();
            if (classes.length == 1) {
              return classes[0];
            }
          }
        }
      }
    }
    return null;
  }

  protected abstract List<VirtualFile> getClassRoots();

  @NotNull
  @Override
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return PsiClass.EMPTY_ARRAY;
    }

    List<PsiClass> result = new ArrayList<PsiClass>();
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final String pkgName = psiPackage.getName();
        final VirtualFile dir = pkgName != null ? classRoot.findFileByRelativePath(pkgName.replace('.', '/')) : classRoot;
        if (dir != null && dir.isDirectory()) {
          for (final VirtualFile file : dir.getChildren()) {
            if (!file.isDirectory()) {
              final PsiFile psi = PsiManager.getInstance(myProject).findFile(file);
              if (psi instanceof PsiClassOwner) {
                result.addAll(Arrays.asList(((PsiClassOwner)psi).getClasses()));
              }
            }
          }
        }
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return null;
    }

    for (final VirtualFile classRoot : classRoots) {
      final VirtualFile dir = classRoot.findFileByRelativePath(qualifiedName.replace('.', '/'));
      if (dir != null && dir.isDirectory()) {
        return createPackage(qualifiedName);
      }
    }
    return null;
  }

  private PsiPackageImpl createPackage(String qualifiedName) {
    return new PsiPackageImpl((PsiManagerEx)PsiManager.getInstance(myProject), qualifiedName);
  }

  @NotNull
  @Override
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return super.getSubPackages(psiPackage, scope);
    }

    List<PsiPackage> result = new ArrayList<PsiPackage>();
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final String pkgName = psiPackage.getName();
        final VirtualFile dir = pkgName != null ? classRoot.findFileByRelativePath(pkgName.replace('.', '/')) : classRoot;
        if (dir != null && dir.isDirectory()) {
          for (final VirtualFile file : dir.getChildren()) {
            if (file.isDirectory()) {
              result.add(createPackage(pkgName + "." + file.getName()));
            }
          }
        }
      }
    }
    return result.toArray(new PsiPackage[result.size()]);
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final PsiClass psiClass = findClass(qualifiedName, scope);
    return psiClass == null ? PsiClass.EMPTY_ARRAY : new PsiClass[]{psiClass};
  }
}
