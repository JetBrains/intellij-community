// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.json.psi.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public final class JsonStructureViewElement implements StructureViewTreeElement {
  private final JsonElement myElement;

  public JsonStructureViewElement(@NotNull JsonElement element) {
    assert PsiTreeUtil.instanceOf(element, JsonFile.class, JsonProperty.class, JsonObject.class, JsonArray.class);
    myElement = element;
  }

  @Override
  public JsonElement getValue() {
    return myElement;
  }

  @Override
  public void navigate(boolean requestFocus) {
    myElement.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myElement.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myElement.canNavigateToSource();
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    final ItemPresentation presentation = myElement.getPresentation();
    assert presentation != null;
    return presentation;
  }

  @Override
  public TreeElement @NotNull [] getChildren() {
    JsonElement value = null;
    if (myElement instanceof JsonFile) {
      value = ((JsonFile)myElement).getTopLevelValue();
    }
    else if (myElement instanceof JsonProperty) {
      value = ((JsonProperty)myElement).getValue();
    }
    else if (PsiTreeUtil.instanceOf(myElement, JsonObject.class, JsonArray.class)) {
      value = myElement;
    }
    if (value instanceof JsonObject object) {
      return ContainerUtil.map2Array(object.getPropertyList(), TreeElement.class, property -> new JsonStructureViewElement(property));
    }
    else if (value instanceof JsonArray array) {
      final List<TreeElement> childObjects = ContainerUtil.mapNotNull(array.getValueList(), value1 -> {
        if (value1 instanceof JsonObject && !((JsonObject)value1).getPropertyList().isEmpty()) {
          return new JsonStructureViewElement(value1);
        }
        else if (value1 instanceof JsonArray && PsiTreeUtil.findChildOfType(value1, JsonProperty.class) != null) {
          return new JsonStructureViewElement(value1);
        }
        return null;
      });
      return childObjects.toArray(TreeElement.EMPTY_ARRAY);
    }
    return EMPTY_ARRAY;
  }
}
