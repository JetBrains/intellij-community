package com.intellij.json.structureView;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider, StructureViewModel.ExpandInfoProvider{

  public JsonStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
    super(psiFile, editor, new JsonStructureViewElement((JsonFile)psiFile));
    withSorters(Sorter.ALPHA_SORTER);
    withSuitableClasses(JsonFile.class, JsonProperty.class);
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    return false;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    return false;
  }

  @Override
  public boolean isAutoExpand(@NotNull StructureViewTreeElement element) {
    final Object value = element.getValue();
    return value instanceof PsiFile || value instanceof JsonProperty;
  }

  @Override
  public boolean isSmartExpand() {
    return false;
  }
}
