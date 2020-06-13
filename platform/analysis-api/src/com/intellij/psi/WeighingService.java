// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public final class WeighingService {
  private static final KeyedExtensionCollector<Weigher, Key> COLLECTOR = new KeyedExtensionCollector<>("com.intellij.weigher");

  private WeighingService() { }

  @NotNull
  public static <T, Loc> WeighingComparable<T, Loc> weigh(Key<? extends Weigher<T, Loc>> key, T element, @Nullable Loc location) {
    return weigh(key, new Computable.PredefinedValueComputable<>(element), location);
  }

  @NotNull
  public static <T, Loc> WeighingComparable<T, Loc> weigh(Key<? extends Weigher<T, Loc>> key,
                                                          Computable<? extends T> element,
                                                          @Nullable Loc location) {
    @SuppressWarnings("unchecked") Weigher<T, Loc>[] array = getWeighers(key).toArray(new Weigher[0]);
    return new WeighingComparable<>(element, location, array);
  }

  public static <T, Loc> List<Weigher> getWeighers(Key<? extends Weigher<T, Loc>> key) {
    return COLLECTOR.forKey(key);
  }
}