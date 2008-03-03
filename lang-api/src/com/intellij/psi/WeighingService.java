/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class WeighingService {
  private static final KeyedExtensionCollector<Weigher,Key> COLLECTOR = new KeyedExtensionCollector<Weigher, Key>("com.intellij.weigher") {
    protected String keyToString(final Key key) {
      return key.toString();
    }
  };

  private WeighingService() {
  }

  @Nullable
  public static <T,Loc> WeighingComparable<T,Loc> weigh(Key<? extends Weigher<T,Loc>> key, T element, Loc location) {
    final List<Weigher> weighers = COLLECTOR.forKey(key);
    return new WeighingComparable<T,Loc>(element, location, weighers.toArray(new Weigher[weighers.size()]));
  }

}
