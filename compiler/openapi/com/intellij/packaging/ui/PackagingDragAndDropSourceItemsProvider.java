package com.intellij.packaging.ui;

import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class PackagingDragAndDropSourceItemsProvider {
  private String myName;

  protected PackagingDragAndDropSourceItemsProvider(String name) {
    myName = name;
  }

  public String getGroupName() {
    return myName;
  }

  @NotNull
  public abstract Collection<? extends PackagingSourceItem> getSourceItems(PackagingEditorContext editorContext, Artifact artifact);
}
