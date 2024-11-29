// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ExecutorsQuery<Result, Parameter> extends AbstractQuery<Result> {
  private static final Logger LOG = Logger.getInstance(ExecutorsQuery.class);

  private final List<? extends QueryExecutor<Result, Parameter>> myExecutors;
  private final Parameter myParameters;

  public ExecutorsQuery(final @NotNull Parameter params, @NotNull List<? extends QueryExecutor<Result, Parameter>> executors) {
    myParameters = params;
    myExecutors = executors;
  }

  @Override
  protected boolean processResults(final @NotNull Processor<? super Result> consumer) {
    for (QueryExecutor<Result, Parameter> executor : myExecutors) {
      try {
        ProgressManager.checkCanceled();
        if (!executor.execute(myParameters, consumer)) {
          return false;
        }
      }
      catch (IndexNotReadyException ignore) {
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    return true;
  }

  @Experimental
  @Override
  public @NotNull Query<Result> wrap(@NotNull QueryWrapper<Result> wrapper) {
    return new ExecutorsQuery<>(myParameters, ContainerUtil.map(myExecutors, e -> e.wrap(wrapper)));
  }
}
