package com.intellij.psi.search;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiReference;
import com.intellij.util.AbstractQuery;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
* @author peter
*/
public class SearchRequestQuery extends AbstractQuery<PsiReference> {
  private final Project myProject;
  private final SearchRequestCollector myRequests;

  public SearchRequestQuery(@NotNull Project project, @NotNull SearchRequestCollector requests) {
    myProject = project;
    myRequests = requests;
  }

  @NotNull
  @Override
  protected AsyncFuture<Boolean> processResultsAsync(@NotNull Processor<PsiReference> consumer) {
    return PsiSearchHelper.SERVICE.getInstance(myProject).processRequestsAsync(myRequests, consumer);
  }

  @Override
  protected boolean processResults(@NotNull Processor<PsiReference> consumer) {
    return PsiSearchHelper.SERVICE.getInstance(myProject).processRequests(myRequests, consumer);
  }
}
