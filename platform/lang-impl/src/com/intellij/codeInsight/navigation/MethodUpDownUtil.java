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

package com.intellij.codeInsight.navigation;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class MethodUpDownUtil {
  private MethodUpDownUtil() {
  }

  public static int[] getNavigationOffsets(PsiFile file, final int caretOffset) {
    for(MethodNavigationOffsetProvider provider: Extensions.getExtensions(MethodNavigationOffsetProvider.EP_NAME)) {
      final int[] offsets = provider.getMethodNavigationOffsets(file, caretOffset);
      if (offsets != null && offsets.length > 0) {
        return offsets;
      }
    }

    Collection<PsiElement> array = new THashSet<>();
    addNavigationElements(array, file);
    return offsetsFromElements(array);
  }

  public static int[] offsetsFromElements(final Collection<PsiElement> array) {
    TIntArrayList offsets = new TIntArrayList(array.size());
    for (PsiElement element : array) {
      int offset = element.getTextOffset();
      assert offset >= 0 : element + " ("+element.getClass()+"); offset: " + offset;
      offsets.add(offset);
    }
    offsets.sort();
    return offsets.toNativeArray();
  }

  private static void addNavigationElements(Collection<PsiElement> array, PsiFile element) {
    StructureViewBuilder structureViewBuilder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(element);
    if (structureViewBuilder instanceof TreeBasedStructureViewBuilder) {
      TreeBasedStructureViewBuilder builder = (TreeBasedStructureViewBuilder) structureViewBuilder;
      StructureViewModel model = builder.createStructureViewModel(null);
      try {
        addStructureViewElements(model.getRoot(), array, element);
      }
      finally {
        model.dispose();
      }
    }
  }

  private static void addStructureViewElements(final TreeElement parent, final Collection<PsiElement> array, @NotNull PsiFile file) {
    for(TreeElement treeElement: parent.getChildren()) {
      Object value = ((StructureViewTreeElement)treeElement).getValue();
      if (value instanceof PsiElement) {
        PsiElement element = (PsiElement)value;
        if (array.contains(element) || !file.equals(element.getContainingFile())) continue;
        array.add(element);
      }
      addStructureViewElements(treeElement, array, file);
    }
  }
}