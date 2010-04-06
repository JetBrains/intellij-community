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

package com.intellij.ide.structureView;

import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class StructureViewModelBase extends TextEditorBasedStructureViewModel {
  private final StructureViewTreeElement myRoot;
  private Sorter[] mySorters = Sorter.EMPTY_ARRAY;
  private Class[] mySuitableClasses = null;

  public StructureViewModelBase(@NotNull PsiFile psiFile, @NotNull StructureViewTreeElement root) {
    super(psiFile);
    myRoot = root;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return myRoot;
  }

  public StructureViewModelBase withSorters(Sorter... sorters) {
    mySorters = sorters;
    return this;
  }

  public StructureViewModelBase withSuitableClasses(Class... suitableClasses) {
    mySuitableClasses = suitableClasses;
    return this;
  }

  @NotNull
  @Override
  public Sorter[] getSorters() {
    return mySorters;
  }

  @NotNull
  @Override
  protected Class[] getSuitableClasses() {
    if (mySuitableClasses != null) {
      return mySuitableClasses;
    }
    return super.getSuitableClasses();
  }
}
