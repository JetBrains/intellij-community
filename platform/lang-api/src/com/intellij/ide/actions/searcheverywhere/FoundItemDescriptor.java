// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

/**
 * @deprecated The old Search Everywhere API is being sunset.
 * Use {@code com.intellij.platform.searchEverywhere.SeItem} instead.
 */
@Deprecated
public class FoundItemDescriptor<I> {
  private final I item;
  private final int weight;

  public FoundItemDescriptor(I item, int weight) {
    this.item = item;
    this.weight = weight;
  }

  public I getItem() {
    return item;
  }

  public int getWeight() {
    return weight;
  }
}
