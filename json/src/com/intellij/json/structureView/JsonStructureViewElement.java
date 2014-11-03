package com.intellij.json.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.json.psi.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonStructureViewElement implements StructureViewTreeElement {
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

  @NotNull
  @Override
  public ItemPresentation getPresentation() {
    final ItemPresentation presentation = myElement.getPresentation();
    assert presentation != null;
    return presentation;
  }

  @NotNull
  @Override
  public TreeElement[] getChildren() {
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
    if (value instanceof JsonObject) {
      final JsonObject object = ((JsonObject)value);
      return ContainerUtil.map2Array(object.getPropertyList(), TreeElement.class, new Function<JsonProperty, TreeElement>() {
        @Override
        public TreeElement fun(JsonProperty property) {
          return new JsonStructureViewElement(property);
        }
      });
    }
    else if (value instanceof JsonArray) {
      final JsonArray array = (JsonArray)value;
      final List<TreeElement> childObjects = ContainerUtil.mapNotNull(array.getValueList(), new Function<JsonValue, TreeElement>() {
        @Override
        public TreeElement fun(JsonValue value) {
          if (value instanceof JsonObject && !((JsonObject)value).getPropertyList().isEmpty()) {
            return new JsonStructureViewElement(value);
          }
          else if (value instanceof JsonArray && PsiTreeUtil.findChildOfType(value, JsonProperty.class) != null) {
            return new JsonStructureViewElement(value);
          }
          return null;
        }
      });
      return ArrayUtil.toObjectArray(childObjects, TreeElement.class);
    }
    return EMPTY_ARRAY;
  }
}
