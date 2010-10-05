package com.intellij.openapi.application;

import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

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
  public final boolean execute(@NotNull final Params queryParameters, @NotNull final Processor<Result> consumer) {
    final AtomicBoolean toContinue = new AtomicBoolean(true);
    final Processor<Result> wrapper = new Processor<Result>() {
      @Override
      public boolean process(Result result) {
        if (!toContinue.get()) {
          return false;
        }

        if (!consumer.process(result)) {
          toContinue.set(false);
          return false;
        }
        return true;
      }
    };

    if (myRequireReadAction) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          processQuery(queryParameters, wrapper);
        }
      });
    }
    else {
      processQuery(queryParameters, wrapper);
    }

    return toContinue.get();
  }

  public abstract void processQuery(@NotNull Params queryParameters, @NotNull Processor<Result> consumer);
}
