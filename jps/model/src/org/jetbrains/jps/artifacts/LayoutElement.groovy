package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project

/**
 * @author nik
 */
abstract class LayoutElement {
  boolean process(Project project, Closure processor) {
    return processor(this)
  }
}

class FileCopyElement extends LayoutElement {
  String filePath
  String outputFileName
}

class DirectoryCopyElement extends LayoutElement {
  String dirPath
}

class ExtractedDirectoryElement extends LayoutElement {
  String jarPath
  String pathInJar
}

class ModuleOutputElement extends LayoutElement {
  String moduleName

}

class ModuleTestOutputElement extends LayoutElement {
  String moduleName
}
