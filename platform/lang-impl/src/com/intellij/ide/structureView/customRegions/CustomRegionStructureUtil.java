/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.structureView.customRegions;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.folding.CustomFoldingProvider;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class CustomRegionStructureUtil {

  public static Collection<StructureViewTreeElement> groupByCustomRegions(@NotNull PsiElement rootElement,
                                                                          @NotNull Collection<StructureViewTreeElement> originalElements) {
    if (rootElement instanceof StubBasedPsiElement &&
        ((StubBasedPsiElement)rootElement).getStub() != null) {
      return originalElements;
    }
    Collection<CustomRegionTreeElement> customRegions = collectCustomRegions(rootElement);
    if (customRegions.size() > 0) {
      List<StructureViewTreeElement> result = new ArrayList<>();
      result.addAll(customRegions);
      for (StructureViewTreeElement element : originalElements) {
        boolean isInCustomRegion = false;
        for (CustomRegionTreeElement customRegion : customRegions) {
          if (customRegion.containsElement(element)) {
            customRegion.addChild(element);
            isInCustomRegion = true;
            break;
          }
        }
        if (!isInCustomRegion) result.add(element);
      }
      return result;
    }
    return originalElements;
  }

  private static Collection<CustomRegionTreeElement> collectCustomRegions(@NotNull PsiElement rootElement) {
    List<CustomRegionTreeElement> customRegions = new ArrayList<>();
    PsiElement child = rootElement.getFirstChild();
    CustomRegionTreeElement currRegionElement = null;
    CustomFoldingProvider provider = null;
    while (child != null) {
      if (child instanceof PsiComment && !child.textContains('\n')) {
        if (provider == null) provider = getProvider(child);
        if (provider != null) {
          String commentText = child.getText();
          if (provider.isCustomRegionStart(commentText)) {
            if (currRegionElement == null) {
              currRegionElement = new CustomRegionTreeElement(child, provider);
              customRegions.add(currRegionElement);
            }
            else {
              currRegionElement = currRegionElement.createNestedRegion(child);
            }
          }
          else if (provider.isCustomRegionEnd(commentText) && currRegionElement != null) {
            currRegionElement = currRegionElement.endRegion(child);
          }
        }
      }
      child = child.getNextSibling();
    }
    return customRegions;
  }

  @Nullable
  static CustomFoldingProvider getProvider(@NotNull PsiElement element) {
    for (CustomFoldingProvider provider : CustomFoldingProvider.getAllProviders()) {
      if (provider.isCustomRegionStart(element.getNode().getText())) {
        return provider;
      }
    }
    return null;
  }
}
