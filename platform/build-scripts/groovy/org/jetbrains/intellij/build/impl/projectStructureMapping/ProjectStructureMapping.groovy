// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildPaths

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

/**
 * Provides mapping between files in the product distribution and modules and libraries in the project configuration. The generated JSON file
 * contains array of {@link DistributionFileEntry}.
 */
@CompileStatic
final class ProjectStructureMapping {
  private final Collection<DistributionFileEntry> entries

  private static final Path MAVEN_REPO = Path.of(System.getProperty("user.home"), ".m2/repository")

  ProjectStructureMapping() {
    entries = new ConcurrentLinkedQueue<>()
  }

  ProjectStructureMapping(@NotNull Collection<DistributionFileEntry> entries) {
    this.entries = Collections.unmodifiableCollection(entries)
  }

  void processEntries(Consumer<DistributionFileEntry> consumer) {
    entries.forEach(consumer)
  }

  void addEntry(DistributionFileEntry entry) {
    entries.add(entry)
  }

  Set<String> getIncludedModules() {
    Set<String> result = new LinkedHashSet<String>()
    entries.findAll { it instanceof ModuleOutputEntry }.collect(result) { ((ModuleOutputEntry)it).moduleName }
    return result
  }

  void generateJsonFile(Path file, BuildPaths buildPaths, Path extraRoot = null) {
    writeReport(entries, file, buildPaths, extraRoot)
  }

  @SuppressWarnings("ChangeToOperator")
  static void writeReport(Collection<DistributionFileEntry> entries, Path file, BuildPaths buildPaths, Path extraRoot = null) {
    Files.createDirectories(file.parent)

    List<DistributionFileEntry> allEntries = new ArrayList<>(entries)

    // sort - stable result
    allEntries.sort({ DistributionFileEntry a , DistributionFileEntry b -> a.path.compareTo(b.path) })

    Files.newOutputStream(file).withCloseable { out ->
      JsonGenerator writer = new JsonFactory().createGenerator(out).useDefaultPrettyPrinter()
      writer.writeStartArray()
      for (DistributionFileEntry entry : allEntries) {
        writer.writeStartObject()
        writer.writeStringField("path", shortenPath(entry.path, buildPaths, extraRoot))
        writer.writeStringField("type", entry.type)
        if (entry instanceof ModuleLibraryFileEntry) {
          writer.writeStringField("module", entry.moduleName)
          writer.writeStringField("libraryFile", shortenPath(entry.libraryFile, buildPaths, extraRoot))
          writer.writeNumberField("size", entry.size)
        }
        else if (entry instanceof ModuleOutputEntry) {
          writer.writeStringField("module", entry.moduleName)
          writer.writeNumberField("size", entry.size)
        }
        else if (entry instanceof ModuleTestOutputEntry) {
          writer.writeStringField("module", entry.moduleName)
        }
        else if (entry instanceof ProjectLibraryEntry) {
          writer.writeStringField("library", entry.libraryName)
          writer.writeStringField("libraryFile", shortenPath(entry.libraryFile, buildPaths, extraRoot))
          writer.writeNumberField("size", entry.size)
        }
        writer.writeEndObject()
      }
      writer.writeEndArray()
    }
  }

  static void buildJarContentReport(Collection<DistributionFileEntry> entries, OutputStream out, BuildPaths buildPaths) {
    JsonGenerator writer = new JsonFactory().createGenerator(out).useDefaultPrettyPrinter()
    Map<Path, List<DistributionFileEntry>> fileToEntry = new TreeMap<>()
    for (DistributionFileEntry entry : entries) {
      fileToEntry.computeIfAbsent(entry.path, { new ArrayList<DistributionFileEntry>() }).add(entry)
    }

    writer.writeStartArray()
    for (Map.Entry<Path, List<DistributionFileEntry>> entrySet : fileToEntry.entrySet()) {
      List<DistributionFileEntry> fileEntries = entrySet.value
      Path file = entrySet.key
      writer.writeStartObject()
      writer.writeStringField("name", shortenPath(file, buildPaths, null))

      writer.writeArrayFieldStart("children")
      writeProjectLibs(fileEntries, writer, buildPaths)
      for (DistributionFileEntry entry : fileEntries) {
        if (entry instanceof ProjectLibraryEntry) {
          continue
        }

        writer.writeStartObject()

        long fileSize
        if (entry instanceof ModuleOutputEntry) {
          writer.writeStringField("name", entry.moduleName)
          fileSize = entry.size
        }
        else if (entry instanceof ModuleLibraryFileEntry) {
          writer.writeStringField("name", shortenPath(entry.libraryFile, buildPaths, null))
          writer.writeStringField("module", entry.moduleName)
          fileSize = Files.size(entry.libraryFile)
        }
        else {
          throw new IllegalStateException("Unsupported entry: $entry")
        }

        writer.writeNumberField("value", fileSize)
        writer.writeEndObject()
      }
      writer.writeEndArray()

      writer.writeEndObject()
    }
    writer.writeEndArray()
    writer.close()
  }

  private static void writeProjectLibs(@NotNull List<DistributionFileEntry> entries, JsonGenerator writer, BuildPaths buildPaths) {
    // group by library
    Map<String, List<ProjectLibraryEntry>> map = new TreeMap<>()
    for (DistributionFileEntry entry : entries) {
      if (entry instanceof ProjectLibraryEntry) {
        map.computeIfAbsent(entry.libraryName, { new ArrayList<ProjectLibraryEntry>()}).add(entry as ProjectLibraryEntry)
      }
    }

    for (Map.Entry<String, List<ProjectLibraryEntry>> entry : map.entrySet()) {
      writer.writeStartObject()

      writer.writeStringField("name", entry.key)

      writer.writeArrayFieldStart("children")
      for (ProjectLibraryEntry fileEntry : entry.value) {
        writer.writeStartObject()
        writer.writeStringField("name", shortenPath(fileEntry.libraryFile, buildPaths, null))
        writer.writeNumberField("value", fileEntry.size as long)
        writer.writeEndObject()
      }
      writer.writeEndArray()

      writer.writeEndObject()
    }
  }

  private static String shortenPath(Path file, BuildPaths buildPaths, @Nullable Path extraRoot) {
    if (file.startsWith(MAVEN_REPO)) {
      return "\$MAVEN_REPOSITORY\$" + File.separatorChar + MAVEN_REPO.relativize(file).toString()
    }

    Path projectHome = buildPaths.projectHomeDir
    if (file.startsWith(projectHome)) {
      return "\$PROJECT_DIR\$" + File.separatorChar + projectHome.relativize(file).toString()
    }
    else {
      Path buildOutputDir = buildPaths.buildOutputDir
      if (file.startsWith(buildOutputDir)) {
        return buildOutputDir.relativize(file)
      }
      else if (extraRoot != null && file.startsWith(extraRoot)) {
        return extraRoot.relativize(file)
      }
      else {
        return file.toString()
      }
    }
  }
}
