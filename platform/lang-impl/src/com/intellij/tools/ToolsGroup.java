package com.intellij.tools;

import com.intellij.openapi.options.CompoundScheme;

public class ToolsGroup extends CompoundScheme<Tool> {
  public ToolsGroup(final String name) {
    super(name);
  }

  public void moveElementUp(final Tool tool) {
    int index = getElements().indexOf(tool);
    removeElement(tool);
    insertElement(tool, index - 1);
  }

  public void moveElementDown(final Tool tool) {
    int index = getElements().indexOf(tool);
    removeElement(tool);
    insertElement(tool, index + 1);

  }

}
