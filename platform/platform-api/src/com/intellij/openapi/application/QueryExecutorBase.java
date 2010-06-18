package com.intellij.openapi.application;

import com.intellij.openapi.util.Ref;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author peter
 */
public abstract class QueryExecutorBase<Result, Params> implements QueryExecutor<Result, Params> {
  private final boolean myRequireReadAction;

  protected QueryExecutorBase(boolean requireReadAction) {
    myRequireReadAction = requireReadAction;
  }

  protected QueryExecutorBase() {
    this(false);
  }

  @Override
  public final boolean execute(final Params queryParameters, final Processor<Result> consumer) {
    final Ref<Boolean> toContinue = Ref.create(true);
    final Processor<Result> wrapper = new Processor<Result>() {
      @Override
      public boolean process(Result result) {
        if (!toContinue.get()) {
          return false;
        }

        final boolean wantMore = consumer.process(result);
        toContinue.set(wantMore);
        return wantMore;
      }
    };

    final Runnable search = new Runnable() {
      public void run() {
        processQuery(queryParameters, wrapper);
      }
    };
    if (myRequireReadAction) {
      ApplicationManager.getApplication().runReadAction(search);
    } else {
      search.run();
    }

    return toContinue.get();
  }

  public abstract void processQuery(Params queryParameters, Processor<Result> consumer);
}
