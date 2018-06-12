// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.QueryParameters;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An adapter for {@link QueryExecutor} interface which makes it easier to write implementations. It provides a possibility to
 * automatically wrap the implementation code into a read action. During indexing, query executors that don't implement {@link com.intellij.openapi.project.DumbAware}
 * (but need to be run in a read action), are delayed until indexing is complete, given that search parameters implement {@link DumbAwareSearchParameters}.
 * <p/>
 * Besides, {@link #processQuery(Object, Processor)} doesn't require to return a boolean value and thus it's harder to stop the whole search
 * by accidentally returning false.
 * 
 * @see Application#runReadAction(Computable) 
 * @see DumbService
 * 
 * @author peter
 */
public abstract class QueryExecutorBase<Result, Params> implements QueryExecutor<Result, Params> {
  private final boolean myRequireReadAction;

  /**
   * @param requireReadAction whether {@link #processQuery(Object, Processor)} should be wrapped into a read action.
   */
  protected QueryExecutorBase(boolean requireReadAction) {
    myRequireReadAction = requireReadAction;
  }

  /**
   * Construct an instance that executes {@link #processQuery(Object, Processor)} as is, without wrapping into a read action.
   */
  protected QueryExecutorBase() {
    this(false);
  }

  @Override
  public final boolean execute(@NotNull final Params queryParameters, @NotNull final Processor<? super Result> consumer) {
    final AtomicBoolean toContinue = new AtomicBoolean(true);
    final Processor<Result> wrapper = result -> {
      if (!toContinue.get()) {
        return false;
      }

      if (!consumer.process(result)) {
        toContinue.set(false);
        return false;
      }
      return true;
    };

    if (myRequireReadAction && !ApplicationManager.getApplication().isReadAccessAllowed()) {
      Runnable runnable = () -> {
        if (!(queryParameters instanceof QueryParameters) || ((QueryParameters)queryParameters).isQueryValid()) {
          processQuery(queryParameters, wrapper);
        }
      };
      
      if (!DumbService.isDumbAware(this)) {
        Project project = queryParameters instanceof QueryParameters ? ((QueryParameters)queryParameters).getProject() : null;
        if (project != null) {
          DumbService.getInstance(project).runReadActionInSmartMode(runnable);
          return toContinue.get();
        }
      }
      
      ApplicationManager.getApplication().runReadAction(runnable);
    }
    else {
      processQuery(queryParameters, wrapper);
    }

    return toContinue.get();
  }

  /**
   * Find some results according to queryParameters and feed them to consumer. If consumer returns false, stop.
   */
  public abstract void processQuery(@NotNull Params queryParameters, @NotNull Processor<? super Result> consumer);
}
