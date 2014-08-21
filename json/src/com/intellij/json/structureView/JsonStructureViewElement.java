package com.intellij.json.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.json.psi.JsonElement;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.navigation.ItemPresentation;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class JsonStructureViewElement implements StructureViewTreeElement {
  private final JsonElement myElement;

  public JsonStructureViewElement(@NotNull JsonElement element) {
    assert element instanceof JsonFile || element instanceof JsonObject || element instanceof JsonProperty;
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
    if (myElement instanceof JsonObject) {
      return new ItemPresentation() {
        @Nullable
        @Override
        public String getPresentableText() {
          return "Object";
        }

        @Nullable
        @Override
        public String getLocationString() {
          return null;
        }

        @Nullable
        @Override
        public Icon getIcon(boolean unused) {
          return PlatformIcons.CLASS_ICON;
        }
      };
    }
    else if (myElement instanceof JsonProperty) {
      return new ItemPresentation() {
        @Nullable
        @Override
        public String getPresentableText() {
          return ((JsonProperty)myElement).getName();
        }

        @Nullable
        @Override
        public String getLocationString() {
          return null;
        }

        @Nullable
        @Override
        public Icon getIcon(boolean unused) {
          return PlatformIcons.PROPERTY_ICON;
        }
      };
    }
    else if (myElement instanceof JsonFile) {
      //noinspection ConstantConditions
      return myElement.getPresentation();
    }
    throw new AssertionError("Attempting to create presentation for invalid element: " + myElement);
  }

  @NotNull
  @Override
  public TreeElement[] getChildren() {
    if (myElement instanceof JsonFile) {
      final JsonFile jsonFile = (JsonFile)myElement;
      if (jsonFile.getTopLevelValue() instanceof JsonObject) {
        return new TreeElement[]{new JsonStructureViewElement(jsonFile.getTopLevelValue())};
      }
    }
    else if (myElement instanceof JsonProperty) {
      final JsonProperty jsonProperty = (JsonProperty)myElement;
      if (jsonProperty.getValue() instanceof JsonObject) {
        return new TreeElement[]{new JsonStructureViewElement(jsonProperty.getValue())};
      }
    }
    else if (myElement instanceof JsonObject) {
      final JsonObject jsonObject = (JsonObject)myElement;
      return ContainerUtil.map2Array(jsonObject.getPropertyList(), TreeElement.class, new Function<JsonProperty, TreeElement>() {
        @Override
        public TreeElement fun(JsonProperty property) {
          return new JsonStructureViewElement(property);
        }
      });
    }
    return EMPTY_ARRAY;
  }
}
