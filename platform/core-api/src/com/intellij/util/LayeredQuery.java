// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

import static java.util.Collections.emptyList;

public class LayeredQuery<B, R> extends AbstractQuery<R> {

  protected final Query<B> myBaseQuery;
  private final Function<? super B, ? extends Collection<? extends Query<? extends R>>> myTransform;

  public LayeredQuery(@NotNull Query<B> query,
                      @NotNull Function<? super B, ? extends Collection<? extends Query<? extends R>>> transform) {
    myBaseQuery = query;
    myTransform = transform;
  }

  @Override
  protected boolean processResults(@NotNull Processor<? super R> consumer) {
    return myBaseQuery.forEach(new Processor<B>() {
      @Override
      public boolean process(B b) {
        for (Query<? extends R> subQuery : myTransform.apply(b)) {
          if (!subQuery.forEach(consumer)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LayeredQuery<?, ?> query = (LayeredQuery<?, ?>)o;

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

  public static <B, R> Query<? extends R> mapping(Query<? extends B> query, Function<? super B, ? extends Query<? extends R>> transform) {
    return new LayeredQuery<>(query, it -> {
      Query<? extends R> r = transform.apply(it);
      return r == null ? emptyList() : Collections.singletonList(r);
    });
  }

  public static <B, R> Query<? extends R> flatMapping(Query<? extends B> query,
                                                      Function<? super B, ? extends Collection<? extends R>> transform) {
    return new LayeredQuery<>(query, it -> Collections.singletonList(new CollectionQuery<>(transform.apply(it))));
  }
}
