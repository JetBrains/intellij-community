package com.intellij.packaging.elements;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;

/**
 * @author nik
 */
public abstract class PackagingElement<S> implements PersistentStateComponent<S> {
  private final PackagingElementType myType;

  protected PackagingElement(PackagingElementType type) {
    myType = type;
  }

  public abstract PackagingElementPresentation createPresentation(PackagingEditorContext context);

  public final PackagingElementType getType() {
    return myType;
  }
}
