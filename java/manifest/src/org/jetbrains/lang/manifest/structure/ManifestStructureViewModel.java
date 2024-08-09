package org.jetbrains.lang.manifest.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.psi.Header;
import org.jetbrains.lang.manifest.psi.ManifestFile;

public class ManifestStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {

  public ManifestStructureViewModel(@Nullable Editor editor, PsiFile psiFile) {
    super(psiFile, editor, new ManifestStructureViewElement(psiFile));
  }

  @Override
  @NotNull
  public Sorter @NotNull [] getSorters() {
    return new Sorter[]{Sorter.ALPHA_SORTER};
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    if (element instanceof ManifestFile) {
      return true;
    }
    return false;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    return element.getValue() instanceof Header;
  }

  @Override
  protected Class<?> @NotNull [] getSuitableClasses() {
    return new Class[]{Header.class};
  }
}
