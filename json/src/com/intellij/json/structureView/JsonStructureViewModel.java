package com.intellij.json.structureView;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.application.ApplicationManager;
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
    withSuitableClasses(JsonFile.class, JsonProperty.class, JsonObject.class, JsonArray.class);
    withSorters(Sorter.ALPHA_SORTER);
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
    // TODO Actually root element for file won't be collapsed even without the following check. Find out how to fix it.
    return element.getValue() instanceof PsiFile || ApplicationManager.getApplication().isUnitTestMode();
  }

  @Override
  public boolean isSmartExpand() {
    return false;
  }
}
