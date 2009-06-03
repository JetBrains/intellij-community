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
      if (offsets != null) {
        return offsets;
      }
    }

    Collection<PsiElement> array = new THashSet<PsiElement>();
    addNavigationElements(array, file);
    return offsetsFromElements(array);
  }

  public static int[] offsetsFromElements(final Collection<PsiElement> array) {
    TIntArrayList offsets = new TIntArrayList(array.size());
    for (PsiElement element : array) {
      offsets.add(element.getTextOffset());
    }
    offsets.sort();
    return offsets.toNativeArray();
  }

  private static void addNavigationElements(Collection<PsiElement> array, PsiFile element) {
    StructureViewBuilder structureViewBuilder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(element);
    if (structureViewBuilder instanceof TreeBasedStructureViewBuilder) {
      TreeBasedStructureViewBuilder builder = (TreeBasedStructureViewBuilder) structureViewBuilder;
      StructureViewModel model = builder.createStructureViewModel();
      addStructureViewElements(model.getRoot(), array, element);
    }
  }

  private static void addStructureViewElements(final TreeElement parent, final Collection<PsiElement> array, @NotNull PsiFile file) {
    for(TreeElement treeElement: parent.getChildren()) {
      Object value = ((StructureViewTreeElement)treeElement).getValue();
      if (value instanceof PsiElement) {
        PsiElement element = (PsiElement)value;
        if (array.contains(element) || !file.equals(element.getContainingFile())) return;
        array.add(element);
      }
      addStructureViewElements(treeElement, array, file);
    }
  }
}