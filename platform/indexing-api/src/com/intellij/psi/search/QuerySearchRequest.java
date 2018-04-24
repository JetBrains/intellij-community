// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.psi.PsiReference;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class QuerySearchRequest {
  public final Query<PsiReference> query;
  public final SearchRequestCollector collector;
  public final Processor<PsiReference> processor;

  public QuerySearchRequest(@NotNull Query<PsiReference> query,
                            @NotNull final SearchRequestCollector collector,
                            boolean inReadAction,
                            @NotNull final PairProcessor<PsiReference, SearchRequestCollector> processor) {
    this.query = query;
    this.collector = collector;
    if (inReadAction) {
      this.processor = new ReadActionProcessor<PsiReference>() {
        @Override
        public boolean processInReadAction(PsiReference psiReference) {
          return processor.process(psiReference, collector);
        }
      };
    }
    else {
      this.processor = psiReference -> processor.process(psiReference, collector);
    }
  }

  public boolean runQuery() {
    return query.forEach(processor);
  }

  @Override
  public String toString() {
    return query + " -> " + collector;
  }
}
