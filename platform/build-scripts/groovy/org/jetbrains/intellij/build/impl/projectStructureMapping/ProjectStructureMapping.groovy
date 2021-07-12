// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.google.gson.GsonBuilder
import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

/**
 * Provides mapping between files in the product distribution and modules and libraries in the project configuration. The generated JSON file
 * contains array of {@link DistributionFileEntry}.
 */
@CompileStatic
final class ProjectStructureMapping {
  private final List<DistributionFileEntry> entries = new ArrayList<>()

  List<DistributionFileEntry> getEntries() {
    return Collections.unmodifiableList(entries)
  }

  void addEntry(DistributionFileEntry entry) {
    entries.add(entry)
  }

  void mergeFrom(ProjectStructureMapping mapping, String relativePath) {
    for (DistributionFileEntry entry : mapping.entries) {
      String newPath = relativePath.isEmpty() ? (String)entry.path : "$relativePath/$entry.path"
      entries.add(changePath(entry, newPath))
    }
  }

  private static DistributionFileEntry changePath(DistributionFileEntry entry, String newPath) {
    DistributionFileEntry copy = entry.clone()
    copy.path = newPath
    return copy
  }

  ProjectStructureMapping extractFromSubFolder(String folderPath) {
    ProjectStructureMapping mapping = new ProjectStructureMapping()
    entries.each {
      if (it.path.startsWith(folderPath)) {
        mapping.entries.add(changePath(it, it.path.substring(folderPath.length())))
      }
    }
    return mapping
  }

  Set<String> getIncludedModules() {
    def result = new LinkedHashSet<String>()
    entries.findAll { it instanceof ModuleOutputEntry }.collect(result) { ((ModuleOutputEntry)it).moduleName }
    return result
  }

  void generateJsonFile(Path file) {
    Files.createDirectories(file.parent)
    Files.newBufferedWriter(file).withCloseable {
      new GsonBuilder().setPrettyPrinting().create().toJson(entries, it)
    }
  }
}
