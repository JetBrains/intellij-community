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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.ComplexPackagingElementNode;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.ComplexPackagingElementType;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class ComplexElementSubstitutionParameters {
  private final Set<ComplexPackagingElementType<?>> myTypesToSubstitute = new HashSet<>();
  private final Set<ComplexPackagingElement<?>> mySubstituted = new HashSet<>();

  public void setSubstituteAll() {
    ContainerUtil.addAll(myTypesToSubstitute, PackagingElementFactory.getInstance().getComplexElementTypes());
    mySubstituted.clear();
  }

  public void setSubstituteNone() {
    myTypesToSubstitute.clear();
    mySubstituted.clear();
  }

  public boolean shouldSubstitute(@NotNull ComplexPackagingElement<?> element) {
    final ComplexPackagingElementType<?> type = (ComplexPackagingElementType<?>)element.getType();
    return myTypesToSubstitute.contains(type) || mySubstituted.contains(element);
  }

  public void setShowContent(ComplexPackagingElementType<?> type, boolean showContent) {
    if (showContent) {
      myTypesToSubstitute.add(type);
    }
    else {
      myTypesToSubstitute.remove(type);
    }
    final Iterator<ComplexPackagingElement<?>> iterator = mySubstituted.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getType().equals(type)) {
        iterator.remove();
      }
    }
  }

  public Set<ComplexPackagingElementType<?>> getTypesToSubstitute() {
    return Collections.unmodifiableSet(myTypesToSubstitute);
  }

  public void setShowContent(ComplexPackagingElementNode complexNode) {
    mySubstituted.addAll(complexNode.getPackagingElements());
  }

  public void doNotSubstitute(ComplexPackagingElement<?> element) {
    mySubstituted.remove(element);
  }

  public boolean isShowContentForType(@NotNull ComplexPackagingElementType type) {
    return myTypesToSubstitute.contains(type);
  }

  public boolean isAllSubstituted() {
    return myTypesToSubstitute.containsAll(Arrays.asList(PackagingElementFactory.getInstance().getComplexElementTypes()));
  }

  public boolean isNoneSubstituted() {
    return myTypesToSubstitute.isEmpty() && mySubstituted.isEmpty();
  }

  public void setTypesToShowContent(Collection<ComplexPackagingElementType<?>> types) {
    myTypesToSubstitute.clear();
    myTypesToSubstitute.addAll(types);
  }
}
