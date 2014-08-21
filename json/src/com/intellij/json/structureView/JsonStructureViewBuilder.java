package com.intellij.json.structureView;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Based on structure view implementation for YAML.
 *
 * @author Mikhail Golubev
 */
public class JsonStructureViewBuilder extends TreeBasedStructureViewBuilder {
  private final JsonFile myJsonFile;

  public JsonStructureViewBuilder(@NotNull JsonFile jsonFile) {
    myJsonFile = jsonFile;
  }

  @NotNull
  @Override
  public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
    return new StructureViewModelBase(myJsonFile, editor, new JsonStructureViewElement(myJsonFile))
      .withSorters(Sorter.ALPHA_SORTER)
      .withSuitableClasses(JsonFile.class, JsonObject.class, JsonProperty.class);
  }
}
