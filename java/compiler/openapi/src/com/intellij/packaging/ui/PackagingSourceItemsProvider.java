package com.intellij.packaging.ui;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class PackagingSourceItemsProvider {
  public static final ExtensionPointName<PackagingSourceItemsProvider> EP_NAME = ExtensionPointName.create("com.intellij.packaging.sourceItemProvider");

  @NotNull
  public abstract Collection<? extends PackagingSourceItem> getSourceItems(@NotNull ArtifactEditorContext editorContext, @NotNull Artifact artifact,
                                                                                 @Nullable PackagingSourceItem parent);
}
