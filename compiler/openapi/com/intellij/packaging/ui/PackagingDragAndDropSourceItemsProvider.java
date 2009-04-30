package com.intellij.packaging.ui;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class PackagingDragAndDropSourceItemsProvider {
  public static final ExtensionPointName<PackagingDragAndDropSourceItemsProvider> EP_NAME = ExtensionPointName.create("com.intellij.packaging.sourceItemProvider");

  @NotNull
  public abstract Collection<? extends PackagingSourceItemsGroup> getSourceItems(PackagingEditorContext editorContext, Artifact artifact,
                                                                                 PackagingSourceItemsGroup parent);
}
