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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class JavaFileTreeModel extends TextEditorBasedStructureViewModel implements StructureViewModel.ElementInfoProvider {
  private final PsiJavaFile myFile;

  public JavaFileTreeModel(@NotNull PsiJavaFile file) {
    super(file);
    myFile = file;
  }

  @NotNull
  public Filter[] getFilters() {
    return new Filter[]{new InheritedMembersFilter(),
                        new FieldsFilter(),
                        new PublicElementsFilter()};
  }

  @NotNull
  public Grouper[] getGroupers() {
    return new Grouper[]{new SuperTypesGrouper(), new PropertiesGrouper()};
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return new JavaFileTreeElement(myFile);
  }

  public boolean shouldEnterElement(final Object element) {
    return element instanceof PsiClass;
  }

  @NotNull
  public Sorter[] getSorters() {
    return new Sorter[]{KindSorter.INSTANCE, VisibilitySorter.INSTANCE, Sorter.ALPHA_SORTER};
  }

  protected PsiFile getPsiFile() {
    return myFile;
  }

  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    Object value = element.getValue();
    return value instanceof PsiClass || value instanceof PsiFile;
  }

  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    Object value = element.getValue();
    return value instanceof PsiMethod || value instanceof PsiField;
  }

  @Override
  protected boolean isSuitable(final PsiElement element) {
    if (super.isSuitable(element)) {
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        PsiElement parent = method.getParent();
        if (parent instanceof PsiClass) {
          return ((PsiClass)parent).getQualifiedName() != null;
        }
      }
      else if (element instanceof PsiField) {
        PsiField field = (PsiField)element;
        PsiElement parent = field.getParent();
        if (parent instanceof PsiClass) {
          return ((PsiClass)parent).getQualifiedName() != null;
        }
      }
      else if (element instanceof PsiClass) {
        return ((PsiClass)element).getQualifiedName() != null;
      }
    }
    return false;
  }

  @NotNull
  protected Class[] getSuitableClasses() {
    return new Class[]{PsiClass.class, PsiMethod.class, PsiField.class, PsiJavaFile.class};
  }
}
