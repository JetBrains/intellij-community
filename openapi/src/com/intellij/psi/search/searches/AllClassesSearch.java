/*
 * @author max
 */
package com.intellij.psi.search.searches;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;

public class AllClassesSearch extends ExtensibleQueryFactory<PsiClass, AllClassesSearch.SearchParameters> {
  public static final AllClassesSearch INSTANCE = new AllClassesSearch();

  public static class SearchParameters {
    private final SearchScope myScope;
    private final Project myProject;

    public SearchParameters(final SearchScope scope, final Project project) {
      myScope = scope;
      myProject = project;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public Project getProject() {
      return myProject;
    }
  }

  public static Query<PsiClass> search(SearchScope scope, Project project) {
    return INSTANCE.createQuery(new SearchParameters(scope, project));
  }
}