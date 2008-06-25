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

import java.util.ArrayList;
import java.util.Arrays;

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

    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    addNavigationElements(array, file);
    return offsetsFromElements(array);
  }

  public static int[] offsetsFromElements(final ArrayList<PsiElement> array) {
    int[] offsets = new int[array.size()];
    for(int i = 0; i < array.size(); i++){
      PsiElement e = array.get(i);
      offsets[i] = e.getTextOffset();
    }
    Arrays.sort(offsets);
    return offsets;
  }

  private static void addNavigationElements(ArrayList<PsiElement> array, PsiFile element) {
    StructureViewBuilder structureViewBuilder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder((PsiFile) element);
    if (structureViewBuilder instanceof TreeBasedStructureViewBuilder) {
      TreeBasedStructureViewBuilder builder = (TreeBasedStructureViewBuilder) structureViewBuilder;
      StructureViewModel model = builder.createStructureViewModel();
      addStructureViewElements(model.getRoot(), array);
    }
  }

  private static void addStructureViewElements(final TreeElement parent, final ArrayList<PsiElement> array) {
    for(TreeElement treeElement: parent.getChildren()) {
      Object value = ((StructureViewTreeElement)treeElement).getValue();
      if (value instanceof PsiElement) {
        array.add((PsiElement) value);
      }
      addStructureViewElements(treeElement, array);
    }
  }
}