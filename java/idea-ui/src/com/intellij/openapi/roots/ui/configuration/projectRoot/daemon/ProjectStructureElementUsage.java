package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class ProjectStructureElementUsage {
  public abstract ProjectStructureElement getSourceElement();

  public abstract ProjectStructureElement getContainingElement();

  public abstract String getPresentableName();

  public abstract PlaceInProjectStructure getPlace();

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  public abstract Icon getIcon();

  public abstract void removeSourceElement();
}
