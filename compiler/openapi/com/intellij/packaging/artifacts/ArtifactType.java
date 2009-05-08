package com.intellij.packaging.artifacts;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
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

  public abstract String getDefaultPathForDirectory();

  public abstract String getDefaultPathForJar();

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
}
