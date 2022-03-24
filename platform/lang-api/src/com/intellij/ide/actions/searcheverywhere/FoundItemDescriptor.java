// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

public class FoundItemDescriptor<I> {
  private final static int MAX_WEIGHT = 10_000;
  private final static int ML_WEIGHT_DISABLED_VALUE = -1;

  private final I item;
  private final int weight;
  private final int mlWeight;

  public FoundItemDescriptor(I item, int weight) {
    this.item = item;
    this.weight = weight;
    mlWeight = ML_WEIGHT_DISABLED_VALUE; // not set
  }

  public FoundItemDescriptor(I item, int weight, double mlWeight) {
    this.item = item;
    this.weight = weight;
    this.mlWeight = (int)(mlWeight * MAX_WEIGHT) * 100_000 + this.weight;
  }
  
  public int getMlWeight() {
    return mlWeight;
  }

  public boolean isMlWeightSet() {
    return mlWeight != ML_WEIGHT_DISABLED_VALUE;
  }

  public I getItem() {
    return item;
  }

  public int getWeight() {
    return isMlWeightSet() ? mlWeight : weight;
  }
}
