// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.google.gson.GsonBuilder
import com.intellij.openapi.util.io.FileUtil

/**
 * Provides mapping between files in the product distribution and modules and libraries in the project configuration. The generated JSON file
 * contains array of {@link DistributionFileEntry}.
 */
class ProjectStructureMapping {
  private List<DistributionFileEntry> entries = []

  void addEntry(DistributionFileEntry entry) {
    entries += entry
  }

  void mergeFrom(ProjectStructureMapping mapping, String relativePath) {
    entries += mapping.entries.collect {
      changePath(it, relativePath.isEmpty() ? it.path : "$relativePath/$it.path")
    }
  }

  private static DistributionFileEntry changePath(DistributionFileEntry entry, String newPath) {
    def copy = entry.clone()
    copy.path = newPath
    copy
  }

  ProjectStructureMapping extractFromSubFolder(String folderPath) {
    def mapping = new ProjectStructureMapping()
    entries.each {
      if (it.path.startsWith(folderPath)) {
        mapping.addEntry(changePath(it, it.path.substring(folderPath.length())))
      }
    }
    return mapping
  }

  List<String> getIncludedModules() {
    return entries.findAll { it instanceof ModuleOutputEntry }.collect { ((ModuleOutputEntry)it).moduleName }
  }

  void generateJsonFile(File file) {
    FileUtil.createParentDirs(file)

    file.withWriter {
      new GsonBuilder().setPrettyPrinting().create().toJson(entries, it)
    }
  }
}
