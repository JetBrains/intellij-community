// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;

@SuppressWarnings({"SSBasedInspection", "RedundantSuppression"})
final class MarkerPool {
  private final PsiBuilderImpl builder;
  private final IntArrayList freeStartMarkers = new IntArrayList();
  private final IntArrayList freeErrorItems = new IntArrayList();

  final ArrayList<PsiBuilderImpl.ProductionMarker> list = new ArrayList<>();

  MarkerPool(PsiBuilderImpl builder) {
    this.builder = builder;
    list.add(null); //no marker has id 0
  }

  PsiBuilderImpl.StartMarker allocateStartMarker() {
    if (!freeStartMarkers.isEmpty()) {
      return (PsiBuilderImpl.StartMarker)list.get(freeStartMarkers.popInt());
    }

    PsiBuilderImpl.StartMarker marker = new PsiBuilderImpl.StartMarker(list.size(), builder);
    list.add(marker);
    return marker;
  }

  PsiBuilderImpl.ErrorItem allocateErrorItem() {
    if (!freeErrorItems.isEmpty()) {
      return (PsiBuilderImpl.ErrorItem)list.get(freeErrorItems.popInt());
    }
    
    PsiBuilderImpl.ErrorItem item = new PsiBuilderImpl.ErrorItem(list.size(), builder);
    list.add(item);
    return item;
  }

  void freeMarker(PsiBuilderImpl.ProductionMarker marker) {
    marker.clean();
    (marker instanceof PsiBuilderImpl.StartMarker ? freeStartMarkers : freeErrorItems).push(marker.markerId);
  }

  PsiBuilderImpl.ProductionMarker get(int index) {
    return list.get(index);
  }
}
