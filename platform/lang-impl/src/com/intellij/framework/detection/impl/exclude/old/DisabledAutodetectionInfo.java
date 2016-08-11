/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.framework.detection.impl.exclude.old;

import com.intellij.util.containers.SortedList;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class DisabledAutodetectionInfo {
  private List<DisabledAutodetectionByTypeElement> myElements = new SortedList<>(DisabledAutodetectionByTypeElement.COMPARATOR);

  @Tag("autodetection-disabled")
  @AbstractCollection(surroundWithTag = false)
  public List<DisabledAutodetectionByTypeElement> getElements() {
    return myElements;
  }

  public void setElements(final List<DisabledAutodetectionByTypeElement> elements) {
    myElements = elements;
  }

  public boolean isDisabled(final @NotNull String facetType, final @NotNull String moduleName, String url) {
    DisabledAutodetectionByTypeElement element = findElement(facetType);
    return element != null && element.isDisabled(moduleName, url);
  }

  public void replaceElement(@NotNull String facetTypeId, @Nullable DisabledAutodetectionByTypeElement element) {
    final DisabledAutodetectionByTypeElement old = findElement(facetTypeId);
    if (old != null) {
      myElements.remove(old);
    }
    if (element != null) {
      myElements.add(element);
    }
  }

  @Nullable
  public DisabledAutodetectionByTypeElement findElement(@NotNull String facetTypeId) {
    for (DisabledAutodetectionByTypeElement element : myElements) {
      if (facetTypeId.equals(element.getFacetTypeId())) {
        return element;
      }
    }
    return null;
  }

  public void addDisabled(final @NotNull String facetTypeId) {
    DisabledAutodetectionByTypeElement element = findElement(facetTypeId);
    if (element != null) {
      element.disableInProject();
    }
    else {
      myElements.add(new DisabledAutodetectionByTypeElement(facetTypeId));
    }
  }

  public void addDisabled(final @NotNull String facetTypeId, final @NotNull String moduleName) {
    DisabledAutodetectionByTypeElement element = findElement(facetTypeId);
    if (element != null) {
      element.addDisabled(moduleName);
    }
    else {
      myElements.add(new DisabledAutodetectionByTypeElement(facetTypeId, moduleName));
    }
  }

  public void addDisabled(final @NotNull String facetTypeId, final @NotNull String moduleName, String url, final boolean recursively) {
    DisabledAutodetectionByTypeElement element = findElement(facetTypeId);
    if (element != null) {
      element.addDisabled(moduleName, url, recursively);
    }
    else {
      myElements.add(new DisabledAutodetectionByTypeElement(facetTypeId, moduleName, url, recursively));
    }
  }

  public void addDisabled(final @NotNull String facetTypeId, final @NotNull String moduleName, final @NotNull String... urls) {
    for (String url : urls) {
      addDisabled(facetTypeId, moduleName, url, false);
    }
  }
}
