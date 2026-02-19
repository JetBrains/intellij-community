// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class QueryFactory<Result, Parameters> {
  private final List<QueryExecutor<Result, Parameters>> myExecutors = ContainerUtil.createLockFreeCopyOnWriteList();

  public void registerExecutor(@NotNull QueryExecutor<Result, Parameters> executor) {
    myExecutors.add(executor);
  }

  public void unregisterExecutor(@NotNull QueryExecutor<Result, Parameters> executor) {
    myExecutors.remove(executor);
  }

  /**
   * @return query to perform the search. @param parameters of the search
   */
  public final @NotNull Query<Result> createQuery(@NotNull Parameters parameters) {
    return new ExecutorsQuery<>(parameters, getExecutors());
  }

  protected @NotNull @Unmodifiable List<QueryExecutor<Result, Parameters>> getExecutors() {
    return myExecutors;
  }

  public boolean hasAnyExecutors() {
    return !getExecutors().isEmpty();
  }

  /**
   * @param parameters of the search
   * @return query to perform the search. Obtained results are automatically filtered wrt. equals() relation.
   */
  public final @NotNull Query<Result> createUniqueResultsQuery(@NotNull Parameters parameters) {
    return new UniqueResultsQuery<Result, Result>(createQuery(parameters));
  }

  /**
   * @param parameters      of the search
   * @param mapper          function that maps results to their mapping counterparts.
   * @return query to perform the search. Obtained results are mapped to whatever objects that are automatically filtered wrt. equals()
   *         relation. Storing mapped objects instead of original elements may be wise wrt to memory consumption.
   */
  public final @NotNull <T> Query<Result> createUniqueResultsQuery(@NotNull Parameters parameters, @NotNull Function<? super Result, ? extends T> mapper) {
    return new UniqueResultsQuery<>(createQuery(parameters), mapper);
  }
}
