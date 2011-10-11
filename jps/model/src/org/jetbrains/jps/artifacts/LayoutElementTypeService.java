package org.jetbrains.jps.artifacts;

import groovy.util.Node;
import org.jetbrains.jps.MacroExpander;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter;

/**
 * @author nik
 */
public abstract class LayoutElementTypeService {
  private final String typeId;

  protected LayoutElementTypeService(String typeId) {
    this.typeId = typeId;
  }

  public String getTypeId() {
    return typeId;
  }

  public abstract LayoutElement createElement(Project project, Node tag, MacroExpander macroExpander, ProjectLoadingErrorReporter errorReporter);
}
