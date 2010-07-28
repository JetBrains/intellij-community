package com.intellij.psi.search;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.psi.PsiReference;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.Query;

/**
 * @author peter
 */
public class QuerySearchRequest {
  public final Query<PsiReference> query;
  public final SearchRequestCollector collector;
  public final Processor<PsiReference> processor;

  public QuerySearchRequest(Query<PsiReference> query,
                            final SearchRequestCollector collector,
                            boolean inReadAction, final PairProcessor<PsiReference, SearchRequestCollector> processor) {
    this.query = query;
    this.collector = collector;
    if (inReadAction) {
      this.processor = new ReadActionProcessor<PsiReference>() {
        @Override
        public boolean processInReadAction(PsiReference psiReference) {
              return processor.process(psiReference, collector);
        }
      };
    } else {
      this.processor = new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference psiReference) {
          return processor.process(psiReference, collector);
        }
      };
    }

  }

  public void runQuery() {
    query.forEach(processor);
  }
}
