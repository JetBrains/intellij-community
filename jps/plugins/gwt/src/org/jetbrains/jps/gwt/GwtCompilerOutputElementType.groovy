package org.jetbrains.jps.gwt

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.LayoutElement
import org.jetbrains.jps.artifacts.LayoutElementTypeService
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter

/**
 * @author nik
 */
public class GwtCompilerOutputElementType extends LayoutElementTypeService {
  public GwtCompilerOutputElementType() {
    super("gwt-compiler-output")
  }

  @Override
  public LayoutElement createElement(Project project, Node tag, MacroExpander macroExpander, ProjectLoadingErrorReporter errorReporter) {
    return new GwtCompilerOutputElement(facetId: tag."@facet", errorReporter: errorReporter)
  }
}
