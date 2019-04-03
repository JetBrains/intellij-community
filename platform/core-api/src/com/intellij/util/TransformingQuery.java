// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Collections.emptyList;

public final class TransformingQuery<B, R> extends AbstractQuery<R> {

  private final Query<B> myBaseQuery;
  private final Function<? super B, ? extends Collection<? extends R>> myTransform;

  private TransformingQuery(@NotNull Query<B> query, @NotNull Function<? super B, ? extends Collection<? extends R>> transform) {
    myBaseQuery = query;
    myTransform = transform;
  }

  public Query<B> getBaseQuery() {
    return myBaseQuery;
  }

  public Function<? super B, ? extends Collection<? extends R>> getTransform() {
    return myTransform;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super R> consumer) {
    return myBaseQuery.forEach(new Processor<B>() {
      @Override
      public boolean process(B b) {
        return ContainerUtil.process(myTransform.apply(b), consumer);
      }
    });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TransformingQuery<?, ?> query = (TransformingQuery<?, ?>)o;

    if (!myBaseQuery.equals(query.myBaseQuery)) return false;
    if (!myTransform.equals(query.myTransform)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myBaseQuery.hashCode();
    result = 31 * result + myTransform.hashCode();
    return result;
  }

  public static <R> Query<? extends R> filtering(@NotNull Query<? extends R> query, @NotNull Predicate<? super R> predicate) {
    return new TransformingQuery<>(query, it -> predicate.test(it) ? Collections.singletonList(it) : emptyList());
  }

  public static <B, R> Query<? extends R> mapping(@NotNull Query<? extends B> query, @NotNull Function<? super B, ? extends R> transform) {
    return new TransformingQuery<>(query, it -> {
      R r = transform.apply(it);
      return r == null ? emptyList() : Collections.singletonList(r);
    });
  }

  public static <B, R> Query<? extends R> flatMapping(@NotNull Query<? extends B> query,
                                                      @NotNull Function<? super B, ? extends Collection<? extends R>> transform) {
    return new TransformingQuery<>(query, transform);
  }
}
