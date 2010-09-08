package org.jetbrains.jps.idea

import org.jetbrains.jps.MacroExpander

/**
 * @author nik
 */
class ModuleMacroExpander implements MacroExpander {
  private ProjectMacroExpander projectExpander
  private String moduleBasePath

  ModuleMacroExpander(ProjectMacroExpander projectExpander, String moduleBasePath) {
    this.projectExpander = projectExpander
    this.moduleBasePath = moduleBasePath
  }

  @Override
  String expandMacros(String path) {
    if (path == null) return null

    String answer = projectExpander.expandMacros(path)
    if (moduleBasePath != null) {
      answer = answer.replace("\$MODULE_DIR\$", moduleBasePath)
    }
    return answer
  }

}
