// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter
import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildPaths

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

  static void buildJarContentReport(ProjectStructureMapping projectStructureMapping, Writer out, BuildPaths buildPaths) {
    // jackson is not available in build scripts - use GSON
    JsonWriter writer = new JsonWriter(out)
    writer.setIndent("  ")

    List<DistributionFileEntry> entries = projectStructureMapping.entries
    Map<String, List<DistributionFileEntry>> fileToEntry = new HashMap<>()
    for (DistributionFileEntry entry : entries) {
      fileToEntry.computeIfAbsent(entry.path, { new ArrayList<DistributionFileEntry>() }).add(entry)
    }

    List<String> files = fileToEntry.keySet().toList()
    files.sort(null)

    writer.beginArray()
    for (String file : files) {
      List<DistributionFileEntry> fileEntries = fileToEntry.get(file)
      writer.beginObject()
      writer.name("name").value(file);

      writer.name("children")
      writer.beginArray()
      writeProjectLibs(fileEntries, writer, buildPaths)
      for (DistributionFileEntry entry : fileEntries) {
        if (entry instanceof ProjectLibraryEntry) {
          continue
        }

        writer.beginObject()

        long fileSize = 0
        if (entry instanceof ModuleOutputEntry) {
          writer.name("name").value(entry.moduleName)
          // well, the only possible way to compute the size of the module - calculate it as part of writing to JAR,
          // maybe it will be done later
        }
        else if (entry instanceof ModuleLibraryFileEntry) {
          writer.name("name").value(entry.filePath)
          fileSize = Files.size(Path.of(entry.libraryFilePath))
        }
        else {
          throw new IllegalStateException("Unsupported entry: $entry")
        }

        writer.name("value").value(fileSize)
        writer.endObject()
      }
      writer.endArray()

      writer.endObject()
    }
    writer.endArray()
  }

  private static void writeProjectLibs(List<DistributionFileEntry> entries, JsonWriter writer, BuildPaths buildPaths) {
    Path mavenLocalRepo = Path.of(SystemProperties.getUserHome(), ".m2/repository")
    // group by library
    Map<String, List<ProjectLibraryEntry>> map = new TreeMap<>()
    for (DistributionFileEntry entry : entries) {
      if (entry instanceof ProjectLibraryEntry) {
        map.computeIfAbsent(entry.libraryName, { new ArrayList<ProjectLibraryEntry>()}).add(entry as ProjectLibraryEntry)
      }
    }

    for (Map.Entry<String, List<ProjectLibraryEntry>> entry : map.entrySet()) {
      writer.beginObject()

      writer.name("name").value(entry.key)

      writer.name("children")
      writer.beginArray()
      for (ProjectLibraryEntry fileEntry : entry.value) {
        writer.beginObject()
        writer.name("name").value(shortenPath(fileEntry.libraryFile, buildPaths))
        writer.name("value").value(fileEntry.fileSize as long)
        writer.endObject()
      }
      writer.endArray()

      writer.endObject()
    }
  }

  static String shortenPath(Path libraryFile, BuildPaths buildPaths) {
    Path mavenRepo = Path.of(SystemProperties.getUserHome(), ".m2/repository")
    Path projectHome = Path.of(buildPaths.projectHome)
    if (libraryFile.startsWith(mavenRepo)) {
      return "\$MAVEN_REPOSITORY\$" + File.separatorChar + mavenRepo.relativize(libraryFile).toString()
    }
    else if (libraryFile.startsWith(projectHome)) {
      return "\$PROJECT_DIR\$" + File.separatorChar + projectHome.relativize(libraryFile).toString()
    }
    else {
      return libraryFile.toString()
    }
  }
}
