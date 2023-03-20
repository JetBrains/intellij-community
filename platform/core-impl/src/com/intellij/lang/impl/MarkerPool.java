/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.impl;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

@SuppressWarnings("SSBasedInspection")
final class MarkerPool extends ObjectArrayList<PsiBuilderImpl.ProductionMarker> {
  private final PsiBuilderImpl myBuilder;
  private final IntArrayList myFreeStartMarkers = new IntArrayList();
  private final IntArrayList myFreeErrorItems = new IntArrayList();

  MarkerPool(PsiBuilderImpl builder) {
    myBuilder = builder;
    add(null); //no marker has id 0
  }

  PsiBuilderImpl.StartMarker allocateStartMarker() {
    if (myFreeStartMarkers.size() > 0) {
      return (PsiBuilderImpl.StartMarker)get(myFreeStartMarkers.popInt());
    }

    PsiBuilderImpl.StartMarker marker = new PsiBuilderImpl.StartMarker(size(), myBuilder);
    add(marker);
    return marker;
  }

  PsiBuilderImpl.ErrorItem allocateErrorItem() {
    if (myFreeErrorItems.size() > 0) {
      return (PsiBuilderImpl.ErrorItem)get(myFreeErrorItems.popInt());
    }
    
    PsiBuilderImpl.ErrorItem item = new PsiBuilderImpl.ErrorItem(size(), myBuilder);
    add(item);
    return item;
  }

  void freeMarker(PsiBuilderImpl.ProductionMarker marker) {
    marker.clean();
    (marker instanceof PsiBuilderImpl.StartMarker ? myFreeStartMarkers : myFreeErrorItems).push(marker.markerId);
  }

}
