
package com.intellij.codeInsight.navigation;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.Arrays;

public class MethodUpDownUtil {
  private MethodUpDownUtil() {
  }

  public static int[] getNavigationOffsets(PsiElement element) {
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    addNavigationElements(array, element);
    int[] offsets = new int[array.size()];
    for(int i = 0; i < array.size(); i++){
      PsiElement e = array.get(i);
      offsets[i] = e.getTextOffset();
    }
    Arrays.sort(offsets);
    return offsets;
  }

  private static void addNavigationElements(ArrayList<PsiElement> array, PsiElement element) {
    if (element instanceof PsiJavaFile || element instanceof PsiClass){
      PsiElement[] children = element.getChildren();
      for (PsiElement child : children) {
        if (child instanceof PsiMethod || child instanceof PsiClass) {
          array.add(child);
          addNavigationElements(array, child);
        }
        if (element instanceof PsiClass && child instanceof PsiJavaToken && child.getText().equals("}")) {
          array.add(child);
        }
      }
    }
    else if (element instanceof XmlFile || element instanceof XmlTag) {
      PsiElement parent = element instanceof XmlFile ? element : element.getParent();

      PsiElement[] children = parent.getChildren();
      for (PsiElement child : children) {
        if (child instanceof XmlTag) {
          array.add(child);
        }
      }
      addNavigationElements(array, element.getParent());
    }
    else if (element instanceof PsiFile) {
      StructureViewBuilder structureViewBuilder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder((PsiFile) element);
      if (structureViewBuilder instanceof TreeBasedStructureViewBuilder) {
        TreeBasedStructureViewBuilder builder = (TreeBasedStructureViewBuilder) structureViewBuilder;
        StructureViewModel model = builder.createStructureViewModel();
        addStructureViewElements(model.getRoot(), array);
      }
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