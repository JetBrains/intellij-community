// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  protected AsyncFuture<Boolean> processResultsAsync(@NotNull Processor<? super PsiReference> consumer) {
    return PsiSearchHelper.getInstance(myProject).processRequestsAsync(myRequests, consumer);
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super PsiReference> consumer) {
    return PsiSearchHelper.getInstance(myProject).processRequests(myRequests, consumer);
  }

  @Override
  public String toString() {
    return myRequests.toString();
  }
}
