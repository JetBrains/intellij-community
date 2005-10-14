package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.deadCode.DummyEntryPointsTool;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author max
 */
public class EntryPointsNode extends InspectionNode {
  private static final Icon ENTRY_POINTS = IconLoader.getIcon("/nodes/entryPoints.png");
  public EntryPointsNode(DummyEntryPointsTool tool) {
    super(tool);
  }

  public Icon getIcon(boolean expanded) {
    return ENTRY_POINTS;
  }
}
