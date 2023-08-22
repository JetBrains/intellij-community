// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.structureView;

import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


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
  public @NotNull StructureViewTreeElement getRoot() {
    return myRoot;
  }

  public @NotNull StructureViewModelBase withSorters(Sorter @NotNull ... sorters) {
    mySorters = sorters;
    return this;
  }

  public @NotNull StructureViewModelBase withSuitableClasses(Class @NotNull ... suitableClasses) {
    mySuitableClasses = suitableClasses;
    return this;
  }

  @Override
  public Sorter @NotNull [] getSorters() {
    return mySorters;
  }

  @Override
  protected Class @NotNull [] getSuitableClasses() {
    if (mySuitableClasses != null) {
      return mySuitableClasses;
    }
    return super.getSuitableClasses();
  }
}
