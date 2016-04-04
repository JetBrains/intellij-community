/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class StructureViewModelBase extends TextEditorBasedStructureViewModel {
  private final StructureViewTreeElement myRoot;
  private Sorter[] mySorters = Sorter.EMPTY_ARRAY;
  private Class[] mySuitableClasses = null;

  public StructureViewModelBase(@NotNull PsiFile psiFile, @Nullable Editor editor, @NotNull StructureViewTreeElement root) {
    super(editor, psiFile);

    myRoot = root;
  }

  public StructureViewModelBase(@NotNull PsiFile psiFile, @NotNull StructureViewTreeElement root) {
    this(psiFile, null, root);
  }

  @Override
  @NotNull
  public StructureViewTreeElement getRoot() {
    return myRoot;
  }

  @NotNull
  public StructureViewModelBase withSorters(@NotNull Sorter... sorters) {
    mySorters = sorters;
    return this;
  }

  @NotNull
  public StructureViewModelBase withSuitableClasses(@NotNull Class... suitableClasses) {
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
