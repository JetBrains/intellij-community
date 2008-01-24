package com.intellij.ide.structureView.newStructureView;

public interface TreeActionsOwner {
  void setActionActive(String name, boolean state);

  boolean isActionActive(String name);
}
