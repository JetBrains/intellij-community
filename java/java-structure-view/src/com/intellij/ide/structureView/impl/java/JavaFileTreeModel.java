/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.ui.PlaceHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class JavaFileTreeModel extends TextEditorBasedStructureViewModel implements StructureViewModel.ElementInfoProvider, PlaceHolder<String> {
  private static final Collection<NodeProvider> NODE_PROVIDERS = Arrays.asList(new JavaInheritedMembersNodeProvider(),
                                                                               new JavaAnonymousClassesNodeProvider(),
                                                                               new JavaLambdaNodeProvider());
  private String myPlace;

  public JavaFileTreeModel(@NotNull PsiClassOwner file, @Nullable Editor editor) {
    super(editor, file);
  }

  @Override
  public Filter @NotNull [] getFilters() {
    return new Filter[]{new FieldsFilter(), new PublicElementsFilter()};
  }

  @NotNull
  @Override
  public Collection<NodeProvider> getNodeProviders() {
    return NODE_PROVIDERS;
  }

  @Override
  public Grouper @NotNull [] getGroupers() {
    return new Grouper[]{new SuperTypesGrouper(), new PropertiesGrouper()};
  }

  @Override
  @NotNull
  public StructureViewTreeElement getRoot() {
    return new JavaFileTreeElement(getPsiFile());
  }

  @Override
  public boolean shouldEnterElement(final Object element) {
    return element instanceof PsiClass;
  }

  @Override
  public Sorter @NotNull [] getSorters() {
    return new Sorter[] {
      TreeStructureUtil.isInStructureViewPopup(this) ? KindSorter.POPUP_INSTANCE : KindSorter.INSTANCE,
      VisibilitySorter.INSTANCE,
      AnonymousClassesSorter.INSTANCE,
      Sorter.ALPHA_SORTER};
  }

  @Override
  protected PsiClassOwner getPsiFile() {
    return (PsiClassOwner)super.getPsiFile();
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    Object value = element.getValue();
    return value instanceof PsiClass || value instanceof PsiFile;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    // Classes, anonymous classes and lambdas
    // can be in anywhere, i.e. in fields, methods and blocks
    return false;
  }

  @Override
  protected boolean isSuitable(final PsiElement element) {
    if (super.isSuitable(element)) {
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        PsiClass parent = method.getContainingClass();
        return parent != null
               && (parent.getQualifiedName() != null || parent instanceof PsiAnonymousClass);
      }

      if (element instanceof PsiField) {
        PsiField field = (PsiField)element;
        PsiClass parent = field.getContainingClass();
        return parent != null && parent.getQualifiedName() != null;
      }

      if (element instanceof PsiClass) {
        return ((PsiClass)element).getQualifiedName() != null;
      }

      return element instanceof PsiLambdaExpression;
    }
    return false;
  }

  @Override
  protected Class @NotNull [] getSuitableClasses() {
    return new Class[]{PsiClass.class, PsiMethod.class, PsiField.class, PsiLambdaExpression.class, PsiJavaFile.class};
  }

  @Override
  public void setPlace(@NotNull String place) {
    myPlace = place;
  }

  @Override
  public String getPlace() {
    return myPlace;
  }
}
