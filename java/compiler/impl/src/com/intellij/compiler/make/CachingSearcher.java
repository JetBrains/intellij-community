package com.intellij.compiler.make;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import com.intellij.util.containers.SoftHashMap;

import java.util.Collection;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 15
 * @author 2003
 */
public class CachingSearcher {
  private final Project myProject;
  private final Map<Pair<PsiElement, Boolean>, Collection<PsiReference>> myElementToReferencersMap = new SoftHashMap<Pair<PsiElement, Boolean>, Collection<PsiReference>>();

  public CachingSearcher(Project project) {
    myProject = project;
  }

  public Collection<PsiReference> findReferences(PsiElement element, final boolean ignoreAccessScope) {
    final Pair<PsiElement, Boolean> key = new Pair<PsiElement, Boolean>(element, ignoreAccessScope? Boolean.TRUE : Boolean.FALSE);
    Collection<PsiReference> psiReferences = myElementToReferencersMap.get(key);
    if (psiReferences == null) {
      GlobalSearchScope searchScope = GlobalSearchScope.projectScope(myProject);
      searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes(searchScope, StdFileTypes.JAVA);
      final Query<PsiReference> query = ReferencesSearch.search(element, searchScope, ignoreAccessScope);
      psiReferences = query.findAll();
      myElementToReferencersMap.put(key, psiReferences);
    }
    return psiReferences;
  }

}
