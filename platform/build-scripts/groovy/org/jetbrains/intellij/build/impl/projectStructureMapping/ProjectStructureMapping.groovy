// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
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

  private static final Path MAVEN_REPO = Path.of(SystemProperties.getUserHome(), ".m2/repository")

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

  void generateJsonFile(Path file, BuildPaths buildPaths) {
    Files.createDirectories(file.parent)
    Files.newBufferedWriter(file).withCloseable {
      new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Path.class, new TypeAdapter<Path>() {
        @Override
        void write(JsonWriter out, Path value) throws IOException {
          out.value(shortenPath(value, buildPaths))
        }

        @Override
        Path read(JsonReader reader) throws IOException {
          throw new UnsupportedOperationException()
        }
      }).create().toJson(entries, it)
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
      writer.name("name").value(file)

      writer.name("children")
      writer.beginArray()
      writeProjectLibs(fileEntries, writer, buildPaths)
      for (DistributionFileEntry entry : fileEntries) {
        if (entry instanceof ProjectLibraryEntry) {
          continue
        }

        writer.beginObject()

        long fileSize
        if (entry instanceof ModuleOutputEntry) {
          writer.name("name").value(entry.moduleName)
          fileSize = entry.size
        }
        else if (entry instanceof ModuleLibraryFileEntry) {
          writer.name("name").value(shortenPath(entry.libraryFile, buildPaths))
          writer.name("module").value(entry.moduleName)
          fileSize = Files.size(entry.libraryFile)
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

  private static String shortenPath(Path libraryFile, BuildPaths buildPaths) {
    if (libraryFile.startsWith(MAVEN_REPO)) {
      return "\$MAVEN_REPOSITORY\$" + File.separatorChar + MAVEN_REPO.relativize(libraryFile).toString()
    }

    Path projectHome = buildPaths.projectHomeDir
    if (libraryFile.startsWith(projectHome)) {
      return "\$PROJECT_DIR\$" + File.separatorChar + projectHome.relativize(libraryFile).toString()
    }
    else {
      return libraryFile.toString()
    }
  }
}
