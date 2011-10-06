package org.jetbrains.jps.idea

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.MacroExpander

/**
 * @author nik
 */
public class ProjectMacroExpander implements MacroExpander {
  private Map<String, String> pathVariables
  private String projectBasePath

  ProjectMacroExpander(Map<String, String> pathVariables, String projectBasePath) {
    this.pathVariables = pathVariables
    this.projectBasePath = FileUtil.toSystemIndependentName(projectBasePath)
  }

  @Override
  String expandMacros(String path) {
    if (path == null) return path
    path = path.replace("\$PROJECT_DIR\$", projectBasePath)
    pathVariables.each { name, value ->
      path = path.replace("\$${name}\$", value)
    }
    return path
  }
}
