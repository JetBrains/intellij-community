// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.CeProcessCanceledException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class ExecutorsQuery<Result, Parameter> extends AbstractQuery<Result> {
  private static final Logger LOG = Logger.getInstance(ExecutorsQuery.class);

  private final List<? extends QueryExecutor<Result, Parameter>> executors;
  private final Parameter parameters;

  public ExecutorsQuery(@NotNull Parameter params, @NotNull List<? extends QueryExecutor<Result, Parameter>> executors) {
    parameters = params;
    this.executors = executors;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super Result> consumer) {
    for (QueryExecutor<Result, Parameter> executor : executors) {
      try {
        ProgressManager.checkCanceled();
        if (!executor.execute(parameters, consumer)) {
          return false;
        }
      }
      catch (IndexNotReadyException ignore) {
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (CancellationException e) {
        throw new CeProcessCanceledException(e);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    return true;
  }

  @Experimental
  @Override
  public @NotNull Query<Result> interceptWith(@NotNull QueryExecutionInterceptor interceptor) {
    if (executors.isEmpty()) {
      return new ExecutorsQuery<>(parameters, Collections.emptyList());
    }
    else {
      List<QueryExecutor<Result, Parameter>> result = new ArrayList<>(executors.size());
      for (QueryExecutor<Result, Parameter> executor : executors) {
        result.add((queryParameters, consumer) -> {
          return interceptor.intercept(() -> executor.execute(queryParameters, consumer));
        });
      }
      return new ExecutorsQuery<>(parameters, result);
    }
  }
}
