package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.ArtifactEditorContext;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class SourceItemsDraggingObject extends PackagingElementDraggingObject {
  private final PackagingSourceItem[] mySourceItems;

  public SourceItemsDraggingObject(PackagingSourceItem[] sourceItems) {
    mySourceItems = sourceItems;
  }

  @Override
  public List<PackagingElement<?>> createPackagingElements(ArtifactEditorContext context) {
    final List<PackagingElement<?>> result = new ArrayList<PackagingElement<?>>();
    for (PackagingSourceItem item : mySourceItems) {
      result.addAll(item.createElements(context));
    }
    return result;
  }

}
