package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.util.AbstractQuery;
import com.intellij.util.Processor;

/**
* @author peter
*/
public class SearchRequestQuery extends AbstractQuery<PsiReference> {
  private final Project myProject;
  private final SearchRequestCollector myRequests;

  public SearchRequestQuery(Project project, SearchRequestCollector requests) {
    myProject = project;
    myRequests = requests;
  }

  @Override
  protected boolean processResults(Processor<PsiReference> consumer) {
    return PsiManager.getInstance(myProject).getSearchHelper().processRequests(myRequests, consumer);
  }
}
