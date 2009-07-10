package com.intellij.packaging.artifacts;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.ArtifactValidationManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class ArtifactType {
  public static final ExtensionPointName<ArtifactType> EP_NAME = ExtensionPointName.create("com.intellij.packaging.artifactType");
  private final String myId;
  private final String myTitle;

  protected ArtifactType(@NonNls String id, String title) {
    myId = id;
    myTitle = title;
  }

  public final String getId() {
    return myId;
  }

  public String getPresentableName() {
    return myTitle;
  }

  @NotNull
  public abstract Icon getIcon();

  @Nullable
  public abstract String getDefaultPathFor(@NotNull PackagingSourceItem sourceItem);

  @Nullable
  public abstract String getDefaultPathFor(@NotNull PackagingElement<?> element, @NotNull PackagingElementResolvingContext context);

  public boolean isSuitableItem(@NotNull PackagingSourceItem sourceItem) {
    return true;
  }

  public static ArtifactType[] getAllTypes() {
    return Extensions.getExtensions(EP_NAME);
  }

  @Nullable
  public static ArtifactType findById(@NotNull @NonNls String id) {
    for (ArtifactType type : getAllTypes()) {
      if (id.equals(type.getId())) {
        return type;
      }
    }
    return null;
  }

  @NotNull
  public abstract CompositePackagingElement<?> createRootElement();

  public void checkRootElement(@NotNull CompositePackagingElement<?> rootElement, @NotNull ArtifactValidationManager manager) {
  }
}
