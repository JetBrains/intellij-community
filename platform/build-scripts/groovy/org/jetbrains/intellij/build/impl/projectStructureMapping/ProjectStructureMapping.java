// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.BuildPaths;
import org.jetbrains.intellij.build.impl.ProjectLibraryData;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Provides mapping between files in the product distribution and modules and libraries in the project configuration. The generated JSON file
 * contains array of {@link DistributionFileEntry}.
 */
public final class ProjectStructureMapping {
  public ProjectStructureMapping() {
    entries = new ConcurrentLinkedQueue<DistributionFileEntry>();
  }

  public ProjectStructureMapping(@NotNull Collection<DistributionFileEntry> entries) {
    this.entries = Collections.unmodifiableCollection(entries);
  }

  public void addEntry(DistributionFileEntry entry) {
    entries.add(entry);
  }

  public Set<String> getIncludedModules() {
    Set<String> result = new LinkedHashSet<String>();
    DefaultGroovyMethods.collect(DefaultGroovyMethods.findAll(entries, new Closure<Boolean>(this, this) {
      public Boolean doCall(DistributionFileEntry it) { return it instanceof ModuleOutputEntry; }

      public Boolean doCall() {
        return doCall(null);
      }
    }), result, new Closure<String>(this, this) {
      public String doCall(DistributionFileEntry it) { return ((ModuleOutputEntry)it).getModuleName(); }

      public String doCall() {
        return doCall(null);
      }
    });
    return result;
  }

  public void generateJsonFile(Path file, BuildPaths buildPaths, Path extraRoot) {
    writeReport(entries, file, buildPaths, extraRoot);
  }

  public void generateJsonFile(Path file, BuildPaths buildPaths) {
    generateJsonFile(file, buildPaths, null);
  }

  @SuppressWarnings
  public static void writeReport(Collection<DistributionFileEntry> entries, Path file, BuildPaths buildPaths, Path extraRoot) {
    Files.createDirectories(file.getParent());

    List<DistributionFileEntry> allEntries = new ArrayList<DistributionFileEntry>(entries);

    // sort - stable result
    DefaultGroovyMethods.sort(allEntries, new Closure<Integer>(null, null) {
      public Integer doCall(DistributionFileEntry a, DistributionFileEntry b) { return a.getPath().compareTo(b.getPath()); }
    });

    IOGroovyMethods.withCloseable(Files.newOutputStream(file), new Closure<Void>(null, null) {
      public void doCall(Object out) {
        JsonGenerator writer = new JsonFactory().createGenerator((OutputStream)out).setPrettyPrinter(new IntelliJDefaultPrettyPrinter());
        writer.writeStartArray();
        for (DistributionFileEntry entry : allEntries) {
          writer.writeStartObject();
          writer.writeStringField("path", shortenAndNormalizePath(entry.getPath(), buildPaths, extraRoot));
          writer.writeStringField("type", entry.getType());
          if (entry instanceof ModuleLibraryFileEntry) {
            writer.writeStringField("module", ((ModuleLibraryFileEntry)entry).getModuleName());
            writer.writeStringField("libraryFile",
                                    shortenAndNormalizePath(((ModuleLibraryFileEntry)entry).getLibraryFile(), buildPaths, extraRoot));
            writer.writeNumberField("size", ((ModuleLibraryFileEntry)entry).getSize());
          }
          else if (entry instanceof ModuleOutputEntry) {
            writer.writeStringField("module", ((ModuleOutputEntry)entry).getModuleName());
            writer.writeNumberField("size", ((ModuleOutputEntry)entry).getSize());
          }
          else if (entry instanceof ModuleTestOutputEntry) {
            writer.writeStringField("module", ((ModuleTestOutputEntry)entry).getModuleName());
          }
          else if (entry instanceof ProjectLibraryEntry) {
            writer.writeStringField("library", ((ProjectLibraryEntry)entry).getData().getLibraryName());
            writer.writeStringField("libraryFile",
                                    shortenAndNormalizePath(((ProjectLibraryEntry)entry).getLibraryFile(), buildPaths, extraRoot));
            writer.writeNumberField("size", ((ProjectLibraryEntry)entry).getSize());
          }

          writer.writeEndObject();
        }

        writer.writeEndArray();
      }
    });
  }

  @SuppressWarnings
  public static void writeReport(Collection<DistributionFileEntry> entries, Path file, BuildPaths buildPaths) {
    ProjectStructureMapping.writeReport(entries, file, buildPaths, null);
  }

  public static void buildJarContentReport(Collection<DistributionFileEntry> entries, OutputStream out, final BuildPaths buildPaths) {
    JsonGenerator writer = new JsonFactory().createGenerator(out).setPrettyPrinter(new IntelliJDefaultPrettyPrinter());
    Map<String, List<DistributionFileEntry>> fileToEntry = new TreeMap<String, List<DistributionFileEntry>>();
    Map<Path, String> fileToPresentablePath = new HashMap<Path, String>();
    for (DistributionFileEntry entry : entries) {
      String presentablePath = fileToPresentablePath.computeIfAbsent(entry.getPath(), new Closure<String>(null, null) {
        public String doCall(Path it) { return shortenAndNormalizePath(it, buildPaths, null); }

        public String doCall() {
          return doCall(null);
        }
      });
      fileToEntry.computeIfAbsent(presentablePath, new Closure<ArrayList<DistributionFileEntry>>(null, null) {
        public ArrayList<DistributionFileEntry> doCall(String it) { return new ArrayList<DistributionFileEntry>(); }

        public ArrayList<DistributionFileEntry> doCall() {
          return doCall(null);
        }
      }).add(entry);
    }


    writer.writeStartArray();
    for (Map.Entry<String, List<DistributionFileEntry>> entrySet : ((TreeMap<String, List<DistributionFileEntry>>)fileToEntry).entrySet()) {
      List<DistributionFileEntry> fileEntries = entrySet.getValue();
      String filePath = entrySet.getKey();
      writer.writeStartObject();
      writer.writeStringField("name", filePath);

      writeProjectLibs(fileEntries, writer, buildPaths);
      writeModules(writer, fileEntries, buildPaths);

      writer.writeEndObject();
    }

    writer.writeEndArray();
    writer.close();
  }

  private static void writeModules(JsonGenerator writer, List<DistributionFileEntry> fileEntries, BuildPaths buildPaths) {
    boolean opened = false;
    for (DistributionFileEntry o : fileEntries) {
      if (!(o instanceof ModuleOutputEntry)) {
        continue;
      }


      if (!opened) {
        writer.writeArrayFieldStart("modules");
        opened = true;
      }


      ModuleOutputEntry entry = (ModuleOutputEntry)o;

      writer.writeStartObject();
      String moduleName = entry.getModuleName();
      writer.writeStringField("name", moduleName);
      writer.writeNumberField("size", entry.getSize());

      writeModuleLibraries(fileEntries, moduleName, writer, buildPaths);

      writer.writeEndObject();
    }

    if (opened) {
      writer.writeEndArray();
    }
  }

  private static void writeModuleLibraries(List<DistributionFileEntry> fileEntries,
                                           String moduleName,
                                           JsonGenerator writer,
                                           BuildPaths buildPaths) {
    boolean opened = false;
    for (DistributionFileEntry o : fileEntries) {
      if (!(o instanceof ModuleLibraryFileEntry)) {
        continue;
      }


      ModuleLibraryFileEntry entry = (ModuleLibraryFileEntry)o;
      if (!entry.getModuleName().equals(moduleName)) {
        continue;
      }


      if (!opened) {
        writer.writeArrayFieldStart("libraries");
        opened = true;
      }


      writer.writeStartObject();
      writer.writeStringField("name", shortenAndNormalizePath(entry.getLibraryFile(), buildPaths, null));
      writer.writeNumberField("size", entry.getSize());
      writer.writeEndObject();
    }


    if (opened) {
      writer.writeEndArray();
    }
  }

  private static void writeProjectLibs(@NotNull List<DistributionFileEntry> entries, JsonGenerator writer, BuildPaths buildPaths) {
    // group by library
    Map<ProjectLibraryData, List<ProjectLibraryEntry>> map =
      new TreeMap<ProjectLibraryData, List<ProjectLibraryEntry>>(new Comparator<ProjectLibraryData>() {
        @SuppressWarnings("ChangeToOperator")
        @Override
        public int compare(ProjectLibraryData o1, ProjectLibraryData o2) {
          return o1.getLibraryName().compareTo(o2.getLibraryName());
        }
      });
    for (DistributionFileEntry entry : entries) {
      if (entry instanceof ProjectLibraryEntry) {
        map.computeIfAbsent(((ProjectLibraryEntry)entry).getData(), new Closure<ArrayList<ProjectLibraryEntry>>(null, null) {
          public ArrayList<ProjectLibraryEntry> doCall(ProjectLibraryData it) { return new ArrayList<ProjectLibraryEntry>(); }

          public ArrayList<ProjectLibraryEntry> doCall() {
            return doCall(null);
          }
        }).add(DefaultGroovyMethods.asType(entry, ProjectLibraryEntry.class));
      }
    }


    if (map.isEmpty()) {
      return;
    }


    writer.writeArrayFieldStart("projectLibraries");
    for (Map.Entry<ProjectLibraryData, List<ProjectLibraryEntry>> entry : ((TreeMap<ProjectLibraryData, List<ProjectLibraryEntry>>)map).entrySet()) {
      writer.writeStartObject();

      ProjectLibraryData data = entry.getKey();
      writer.writeStringField("name", data.getLibraryName());

      writer.writeArrayFieldStart("files");
      for (ProjectLibraryEntry fileEntry : entry.getValue()) {
        writer.writeStartObject();
        writer.writeStringField("name", shortenAndNormalizePath(fileEntry.getLibraryFile(), buildPaths, null));
        writer.writeNumberField("size", DefaultGroovyMethods.asType(fileEntry.getSize(), (Class<Object>)Long.class));
        writer.writeEndObject();
      }

      writer.writeEndArray();

      if (data.getReason() != null) {
        writer.writeStringField("reason", data.getReason());
      }

      writeModuleDependents(writer, data);

      writer.writeEndObject();
    }

    writer.writeEndArray();
  }

  private static void writeModuleDependents(JsonGenerator writer, ProjectLibraryData data) {
    writer.writeObjectFieldStart("dependentModules");
    for (Map.Entry<String, List<String>> pluginAndModules : data.getDependentModules().entrySet()) {
      writer.writeArrayFieldStart(pluginAndModules.getKey());
      for (String moduleName : DefaultGroovyMethods.toSorted(pluginAndModules.getValue())) {
        writer.writeString(moduleName);
      }

      writer.writeEndArray();
    }

    writer.writeEndObject();
  }

  private static String shortenPath(Path file, BuildPaths buildPaths, @Nullable Path extraRoot) {
    if (file.startsWith(MAVEN_REPO)) {
      return "\$MAVEN_REPOSITORY\$/" + MAVEN_REPO.relativize(file).toString().replace(File.separatorChar, (char)"/");
    }


    Path projectHome = buildPaths.getProjectHomeDir();
    if (file.startsWith(projectHome)) {
      return "\$PROJECT_DIR\$/" + projectHome.relativize(file).toString();
    }
    else {
      Path buildOutputDir = buildPaths.getBuildOutputDir();
      if (file.startsWith(buildOutputDir)) {
        return buildOutputDir.relativize(file);
      }
      else if (extraRoot != null && file.startsWith(extraRoot)) {
        return extraRoot.relativize(file);
      }
      else {
        return file.toString();
      }
    }
  }

  private static String shortenAndNormalizePath(Path file, BuildPaths buildPaths, @Nullable Path extraRoot) {
    String result = shortenPath(file, buildPaths, extraRoot).replace(File.separatorChar, (char)"/");
    return result.startsWith("temp/") ? result.substring("temp/".length()) : result;
  }

  private final Collection<DistributionFileEntry> entries;
  private static final Path MAVEN_REPO = Path.of(System.getProperty("user.home"), ".m2/repository");

  final private static class IntelliJDefaultPrettyPrinter extends DefaultPrettyPrinter {
    public IntelliJDefaultPrettyPrinter() {
      _objectFieldValueSeparatorWithSpaces = ": ";
      _objectIndenter = INDENTER;
      _arrayIndenter = INDENTER;
    }

    @Override
    public DefaultPrettyPrinter createInstance() {
      return new IntelliJDefaultPrettyPrinter();
    }

    private static final DefaultIndenter INDENTER = new DefaultIndenter("  ", "\n");
  }
}
