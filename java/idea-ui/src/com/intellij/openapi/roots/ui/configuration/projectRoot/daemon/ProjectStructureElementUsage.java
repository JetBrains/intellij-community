package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ProjectStructureElementUsage {
  public abstract ProjectStructureElement getSourceElement();

  public abstract ProjectStructureElement getContainingElement();

  public abstract @NlsContexts.Label String getPresentableName();

  @Nullable
  public @NlsContexts.Label String getPresentableLocationInElement() {
    return null;
  }

  public abstract PlaceInProjectStructure getPlace();

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  public abstract Icon getIcon();

  public abstract void removeSourceElement();

  public abstract void replaceElement(ProjectStructureElement newElement);
}
