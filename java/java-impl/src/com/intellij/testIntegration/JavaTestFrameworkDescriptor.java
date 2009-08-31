package com.intellij.testIntegration;

import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;

public abstract class JavaTestFrameworkDescriptor implements TestFrameworkDescriptor {
  public boolean isLibraryAttached(Module m) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(m);
    PsiClass c = JavaPsiFacade.getInstance(m.getProject()).findClass(getMarkerClassFQName(), scope);
    return c != null;
  }

  protected abstract String getMarkerClassFQName();

 public boolean isTestClass(PsiElement element) {
   return element instanceof PsiClass && isTestClass((PsiClass)element);
 }

 public abstract boolean isTestClass(PsiClass clazz);
}
