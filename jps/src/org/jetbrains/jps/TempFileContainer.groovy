package org.jetbrains.jps

import org.jetbrains.jps.builders.BuildUtil

/**
 * @author nik
 */
class TempFileContainer {
  private File baseDirectory
  private final String tempDirectoryName
  private final Project project
  private final Set<String> usedNames = [] as Set

  TempFileContainer(Project project, String tempDirectoryName) {
    this.project = project
    this.tempDirectoryName = tempDirectoryName
  }

  private File getBaseDirectory() {
    if (baseDirectory == null) {
      def ant = project.binding.ant
      String basePath = project.tempFolder ?: project.targetFolder ?: "."
      baseDirectory = new File(basePath, tempDirectoryName)
      BuildUtil.deleteDir(project, baseDirectory.absolutePath)
      ant.mkdir(dir: baseDirectory.absolutePath)
    }
    return baseDirectory
  }

  String getTempDirPath(String name) {
    String baseName = BuildUtil.suggestFileName(name)
    String dirName = baseName
    int i = 2
    while (usedNames.contains(dirName)) {
      dirName = baseName + i
      i++
    }
    usedNames << dirName
    File tempDir = new File(getBaseDirectory(), dirName)
    return tempDir.absolutePath
  }

  def clean() {
    if (baseDirectory != null) {
      def ant = project.binding.ant
      BuildUtil.deleteDir(project, baseDirectory.absolutePath)
    }

  }
}
