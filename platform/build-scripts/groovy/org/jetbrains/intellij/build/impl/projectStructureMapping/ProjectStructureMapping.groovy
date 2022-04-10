// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.impl.ProjectLibraryData

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
      JsonGenerator writer = new JsonFactory().createGenerator(out).setPrettyPrinter(new IntelliJDefaultPrettyPrinter())
      writer.writeStartArray()
      for (DistributionFileEntry entry : allEntries) {
        writer.writeStartObject()
        writer.writeStringField("path", shortenAndNormalizePath(entry.path, buildPaths, extraRoot))
        writer.writeStringField("type", entry.type)
        if (entry instanceof ModuleLibraryFileEntry) {
          writer.writeStringField("module", entry.moduleName)
          writer.writeStringField("libraryFile", shortenAndNormalizePath(entry.libraryFile, buildPaths, extraRoot))
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
          writer.writeStringField("library", entry.data.libraryName)
          writer.writeStringField("libraryFile", shortenAndNormalizePath(entry.libraryFile, buildPaths, extraRoot))
          writer.writeNumberField("size", entry.size)
        }
        writer.writeEndObject()
      }
      writer.writeEndArray()
    }
  }

  private static final class IntelliJDefaultPrettyPrinter extends DefaultPrettyPrinter {
    private static final DefaultIndenter INDENTER = new DefaultIndenter("  ", "\n")

    IntelliJDefaultPrettyPrinter() {
      _objectFieldValueSeparatorWithSpaces = ": "
      _objectIndenter = INDENTER
      _arrayIndenter = INDENTER
    }

    @Override
    DefaultPrettyPrinter createInstance() {
      return new IntelliJDefaultPrettyPrinter()
    }
  }

  static void buildJarContentReport(Collection<DistributionFileEntry> entries, OutputStream out, BuildPaths buildPaths) {
    JsonGenerator writer = new JsonFactory().createGenerator(out).setPrettyPrinter(new IntelliJDefaultPrettyPrinter())
    Map<String, List<DistributionFileEntry>> fileToEntry = new TreeMap<>()
    Map<Path, String> fileToPresentablePath = new HashMap<>()
    for (DistributionFileEntry entry : entries) {
      String presentablePath = fileToPresentablePath.computeIfAbsent(entry.path, { shortenAndNormalizePath(it, buildPaths, null) })
      fileToEntry.computeIfAbsent(presentablePath, { new ArrayList<DistributionFileEntry>() }).add(entry)
    }

    writer.writeStartArray()
    for (Map.Entry<String, List<DistributionFileEntry>> entrySet : fileToEntry.entrySet()) {
      List<DistributionFileEntry> fileEntries = entrySet.value
      String filePath = entrySet.key
      writer.writeStartObject()
      writer.writeStringField("name", filePath)

      writeProjectLibs(fileEntries, writer, buildPaths)
      writeModules(writer, fileEntries, buildPaths)

      writer.writeEndObject()
    }
    writer.writeEndArray()
    writer.close()
  }

  private static void writeModules(JsonGenerator writer, List<DistributionFileEntry> fileEntries, BuildPaths buildPaths) {
    boolean opened = false
    for (DistributionFileEntry o : fileEntries) {
      if (!(o instanceof ModuleOutputEntry)) {
        continue
      }

      if (!opened) {
        writer.writeArrayFieldStart("modules")
        opened = true
      }

      ModuleOutputEntry entry = (ModuleOutputEntry)o

      writer.writeStartObject()
      String moduleName = entry.moduleName
      writer.writeStringField("name", moduleName)
      writer.writeNumberField("size", entry.size)

      writeModuleLibraries(fileEntries, moduleName, writer, buildPaths)

      writer.writeEndObject()
    }
    if (opened) {
      writer.writeEndArray()
    }
  }

  private static void writeModuleLibraries(List<DistributionFileEntry> fileEntries,
                                           String moduleName,
                                           JsonGenerator writer,
                                           BuildPaths buildPaths) {
    boolean opened = false
    for (DistributionFileEntry o : fileEntries) {
      if (!(o instanceof ModuleLibraryFileEntry)) {
        continue
      }

      ModuleLibraryFileEntry entry = (ModuleLibraryFileEntry)o
      if (entry.moduleName != moduleName) {
        continue
      }

      if (!opened) {
        writer.writeArrayFieldStart("libraries")
        opened = true
      }

      writer.writeStartObject()
      writer.writeStringField("name", shortenAndNormalizePath(entry.libraryFile, buildPaths, null))
      writer.writeNumberField("size", entry.size)
      writer.writeEndObject()
    }

    if (opened) {
      writer.writeEndArray()
    }
  }

  private static void writeProjectLibs(@NotNull List<DistributionFileEntry> entries, JsonGenerator writer, BuildPaths buildPaths) {
    // group by library
    Map<ProjectLibraryData, List<ProjectLibraryEntry>> map = new TreeMap<>(new Comparator<ProjectLibraryData>() {
      @SuppressWarnings("ChangeToOperator")
      @Override
      int compare(ProjectLibraryData o1, ProjectLibraryData o2) {
        return o1.libraryName.compareTo(o2.libraryName)
      }
    })
    for (DistributionFileEntry entry : entries) {
      if (entry instanceof ProjectLibraryEntry) {
        map.computeIfAbsent(entry.data, { new ArrayList<ProjectLibraryEntry>()}).add(entry as ProjectLibraryEntry)
      }
    }

    if (map.isEmpty()) {
      return
    }

    writer.writeArrayFieldStart("projectLibraries")
    for (Map.Entry<ProjectLibraryData, List<ProjectLibraryEntry>> entry : map.entrySet()) {
      writer.writeStartObject()

      ProjectLibraryData data = entry.key
      writer.writeStringField("name", data.libraryName)

      writer.writeArrayFieldStart("files")
      for (ProjectLibraryEntry fileEntry : entry.value) {
        writer.writeStartObject()
        writer.writeStringField("name", shortenAndNormalizePath(fileEntry.libraryFile, buildPaths, null))
        writer.writeNumberField("size", fileEntry.size as long)
        writer.writeEndObject()
      }
      writer.writeEndArray()

      if (data.reason != null) {
        writer.writeStringField("reason", data.reason)
      }
      writeModuleDependents(writer, data)

      writer.writeEndObject()
    }
    writer.writeEndArray()
  }

  private static void writeModuleDependents(JsonGenerator writer, ProjectLibraryData data) {
    writer.writeObjectFieldStart("dependentModules")
    for (Map.Entry<String, List<String>> pluginAndModules : data.dependentModules.entrySet()) {
      writer.writeArrayFieldStart(pluginAndModules.key)
      for (String moduleName : pluginAndModules.value.toSorted()) {
        writer.writeString(moduleName)
      }
      writer.writeEndArray()
    }
    writer.writeEndObject()
  }

  private static String shortenPath(Path file, BuildPaths buildPaths, @Nullable Path extraRoot) {
    if (file.startsWith(MAVEN_REPO)) {
      return "\$MAVEN_REPOSITORY\$/" + MAVEN_REPO.relativize(file).toString().replace(File.separatorChar, (char)'/')
    }

    Path projectHome = buildPaths.projectHomeDir
    if (file.startsWith(projectHome)) {
      return "\$PROJECT_DIR\$/" + projectHome.relativize(file).toString()
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

  private static String shortenAndNormalizePath(Path file, BuildPaths buildPaths, @Nullable Path extraRoot) {
    String result = shortenPath(file, buildPaths, extraRoot).replace(File.separatorChar, (char)'/')
    return result.startsWith("temp/") ? result.substring("temp/".length()) : result
  }
}
