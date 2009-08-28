package com.intellij.ide.structureView;

import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class StructureViewModelBase extends TextEditorBasedStructureViewModel {
  private StructureViewTreeElement myRoot;
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
