package com.intellij.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;

public interface PsiElementFinder {
  public abstract PsiClass findClass(String qualifiedName, GlobalSearchScope scope);
  public abstract PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope);
  public abstract PsiPackage findPackage(String qualifiedName);

  PsiPackage[] getSubPackages(PsiPackage psiPackage, GlobalSearchScope scope);
  PsiClass[] getClasses(PsiPackage psiPackage, GlobalSearchScope scope);
}
