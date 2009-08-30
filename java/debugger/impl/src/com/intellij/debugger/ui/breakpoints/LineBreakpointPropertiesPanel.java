/*
 * Class LineBreakpointPropertiesPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.project.Project;

public class LineBreakpointPropertiesPanel extends BreakpointPropertiesPanel {
  public LineBreakpointPropertiesPanel(Project project) {
    super(project, LineBreakpoint.CATEGORY);
  }
}