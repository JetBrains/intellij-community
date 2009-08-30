package com.intellij.refactoring.util.classRefs;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;

/**
 * @author dsl
 */
public class ClassReferenceSearchingScanner extends ClassReferenceScanner {
  public ClassReferenceSearchingScanner(PsiClass aClass) {
    super(aClass);
  }

  public PsiReference[] findReferences() {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myClass.getProject());
    return ReferencesSearch.search(myClass, projectScope, false).toArray(new PsiReference[0]);
  }

}
