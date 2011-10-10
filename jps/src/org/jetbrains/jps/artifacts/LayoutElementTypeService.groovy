package org.jetbrains.jps.artifacts

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Project
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter

/**
 * @author nik
 */
public abstract class LayoutElementTypeService {
  final String typeId

  LayoutElementTypeService(String typeId) {
    this.typeId = typeId
  }

  public abstract LayoutElement createElement(Project project, Node tag, MacroExpander macroExpander, ProjectLoadingErrorReporter errorReporter)
}
