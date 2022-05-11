// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import groovy.lang.Closure
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.IOGroovyMethods
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Provides mapping between files in the product distribution and modules and libraries in the project configuration. The generated JSON file
 * contains array of [DistributionFileEntry].
 */
class ProjectStructureMapping {
  constructor() {
    entries = ConcurrentLinkedQueue()
  }

  constructor(entries: Collection<DistributionFileEntry>) {
    this.entries = Collections.unmodifiableCollection(entries)
  }

  fun addEntry(entry: DistributionFileEntry) {
    entries.add(entry)
  }

  val includedModules: Set<String>
    get() {
      val result: Set<String> = LinkedHashSet()
      DefaultGroovyMethods.collect(DefaultGroovyMethods.findAll(entries, object : Closure<Boolean?>(this, this) {
        @JvmOverloads
        fun doCall(it: DistributionFileEntry? = null): Boolean {
          return it is ModuleOutputEntry
        }
      }), result, object : Closure<String?>(this, this) {
        @JvmOverloads
        fun doCall(it: DistributionFileEntry? = null): String {
          return (it as ModuleOutputEntry?)!!.moduleName
        }
      })
      return result
    }

  @JvmOverloads
  fun generateJsonFile(file: Path, buildPaths: BuildPaths, extraRoot: Path? = null) {
    writeReport(entries, file, buildPaths, extraRoot)
  }

  private val entries: MutableCollection<DistributionFileEntry>

  private class IntelliJDefaultPrettyPrinter : DefaultPrettyPrinter() {
    override fun createInstance(): DefaultPrettyPrinter {
      return IntelliJDefaultPrettyPrinter()
    }

    init {
      _objectFieldValueSeparatorWithSpaces = ": "
      _objectIndenter = INDENTER
      _arrayIndenter = INDENTER
    }

    companion object {
      private val INDENTER = DefaultIndenter("  ", "\n")
    }
  }

  companion object {
    fun writeReport(entries: Collection<DistributionFileEntry>?, file: Path, buildPaths: BuildPaths, extraRoot: Path?) {
      Files.createDirectories(file.parent)
      val allEntries: List<DistributionFileEntry> = ArrayList(entries)

      // sort - stable result
      DefaultGroovyMethods.sort(allEntries, object : Closure<Int?>(null, null) {
        fun doCall(a: DistributionFileEntry, b: DistributionFileEntry): Int {
          return a.path.compareTo(b.path)
        }
      })
      IOGroovyMethods.withCloseable(Files.newOutputStream(file), object : Closure<Void?>(null, null) {
        fun doCall(out: Any?) {
          val writer = JsonFactory().createGenerator(out as OutputStream?).setPrettyPrinter(IntelliJDefaultPrettyPrinter())
          writer.writeStartArray()
          for (entry in allEntries) {
            writer.writeStartObject()
            writer.writeStringField("path", shortenAndNormalizePath(entry.path, buildPaths, extraRoot))
            writer.writeStringField("type", entry.type)
            if (entry is ModuleLibraryFileEntry) {
              writer.writeStringField("module", entry.moduleName)
              writer.writeStringField("libraryFile",
                                      shortenAndNormalizePath(entry.libraryFile, buildPaths, extraRoot))
              writer.writeNumberField("size", entry.size)
            }
            else if (entry is ModuleOutputEntry) {
              writer.writeStringField("module", entry.moduleName)
              writer.writeNumberField("size", entry.size)
            }
            else if (entry is ModuleTestOutputEntry) {
              writer.writeStringField("module", entry.moduleName)
            }
            else if (entry is ProjectLibraryEntry) {
              writer.writeStringField("library", entry.data.libraryName)
              writer.writeStringField("libraryFile",
                                      shortenAndNormalizePath(entry.libraryFile, buildPaths, extraRoot))
              writer.writeNumberField("size", entry.size)
            }
            writer.writeEndObject()
          }
          writer.writeEndArray()
        }
      })
    }

    fun writeReport(entries: Collection<DistributionFileEntry>?, file: Path, buildPaths: BuildPaths) {
      writeReport(entries, file, buildPaths, null)
    }

    fun buildJarContentReport(entries: Collection<DistributionFileEntry>, out: OutputStream?, buildPaths: BuildPaths) {
      val writer = JsonFactory().createGenerator(out).setPrettyPrinter(IntelliJDefaultPrettyPrinter())
      val fileToEntry: MutableMap<String, MutableList<DistributionFileEntry>> = TreeMap()
      val fileToPresentablePath: MutableMap<Path, String> = HashMap()
      for (entry in entries) {
        val presentablePath = fileToPresentablePath.computeIfAbsent(entry.path, object : Closure<String?>(null, null) {
          @JvmOverloads
          fun doCall(it: Path? = null): String {
            return shortenAndNormalizePath(it, buildPaths, null)
          }
        })
        fileToEntry.computeIfAbsent(presentablePath, object : Closure<ArrayList<DistributionFileEntry?>?>(null, null) {
          @JvmOverloads
          fun doCall(it: String? = null): ArrayList<DistributionFileEntry> {
            return ArrayList()
          }
        }).add(entry)
      }
      writer.writeStartArray()
      for ((filePath, fileEntries) in fileToEntry as TreeMap<String, MutableList<DistributionFileEntry>>) {
        writer.writeStartObject()
        writer.writeStringField("name", filePath)
        writeProjectLibs(fileEntries, writer, buildPaths)
        writeModules(writer, fileEntries, buildPaths)
        writer.writeEndObject()
      }
      writer.writeEndArray()
      writer.close()
    }

    private fun writeModules(writer: JsonGenerator, fileEntries: List<DistributionFileEntry>, buildPaths: BuildPaths) {
      var opened = false
      for (o in fileEntries) {
        if (o !is ModuleOutputEntry) {
          continue
        }
        if (!opened) {
          writer.writeArrayFieldStart("modules")
          opened = true
        }
        val entry = o
        writer.writeStartObject()
        val moduleName = entry.moduleName
        writer.writeStringField("name", moduleName)
        writer.writeNumberField("size", entry.size)
        writeModuleLibraries(fileEntries, moduleName, writer, buildPaths)
        writer.writeEndObject()
      }
      if (opened) {
        writer.writeEndArray()
      }
    }

    private fun writeModuleLibraries(fileEntries: List<DistributionFileEntry>,
                                     moduleName: String,
                                     writer: JsonGenerator,
                                     buildPaths: BuildPaths) {
      var opened = false
      for (o in fileEntries) {
        if (o !is ModuleLibraryFileEntry) {
          continue
        }
        val entry = o
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

    private fun writeProjectLibs(entries: List<DistributionFileEntry>, writer: JsonGenerator, buildPaths: BuildPaths) {
      // group by library
      val map: MutableMap<ProjectLibraryData, MutableList<ProjectLibraryEntry>> = TreeMap { o1, o2 ->
        o1.libraryName.compareTo(o2.libraryName)
      }
      for (entry in entries) {
        if (entry is ProjectLibraryEntry) {
          map.computeIfAbsent(entry.data, object : Closure<ArrayList<ProjectLibraryEntry?>?>(null, null) {
            @JvmOverloads
            fun doCall(it: ProjectLibraryData? = null): ArrayList<ProjectLibraryEntry> {
              return ArrayList()
            }
          }).add(DefaultGroovyMethods.asType(entry, ProjectLibraryEntry::class.java))
        }
      }
      if (map.isEmpty()) {
        return
      }
      writer.writeArrayFieldStart("projectLibraries")
      for ((data, value) in map as TreeMap<ProjectLibraryData, MutableList<ProjectLibraryEntry>>) {
        writer.writeStartObject()
        writer.writeStringField("name", data.libraryName)
        writer.writeArrayFieldStart("files")
        for (fileEntry in value) {
          writer.writeStartObject()
          writer.writeStringField("name", shortenAndNormalizePath(fileEntry.libraryFile, buildPaths, null))
          writer.writeNumberField("size", DefaultGroovyMethods.asType(fileEntry.size, Long::class.java as Class<Any?>))
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

    private fun writeModuleDependents(writer: JsonGenerator, data: ProjectLibraryData) {
      writer.writeObjectFieldStart("dependentModules")
      for ((key, value) in data.dependentModules) {
        writer.writeArrayFieldStart(key)
        for (moduleName in DefaultGroovyMethods.toSorted(value)) {
          writer.writeString(moduleName)
        }
        writer.writeEndArray()
      }
      writer.writeEndObject()
    }

    private fun shortenPath(file: Path?, buildPaths: BuildPaths, extraRoot: Path?): String {
      if (file!!.startsWith(MAVEN_REPO)) {
        return "\\$MAVEN_REPOSITORY\$/" + MAVEN_REPO.relativize(file).toString().replace(File.separatorChar, "/" as Char)
      }
      val projectHome = buildPaths.projectHomeDir
      return if (file.startsWith(projectHome)) {
        "\\$PROJECT_DIR\$/" + projectHome.relativize(file).toString()
      }
      else {
        val buildOutputDir = buildPaths.buildOutputDir
        if (file.startsWith(buildOutputDir)) {
          buildOutputDir.relativize(file)
        }
        else if (extraRoot != null && file.startsWith(extraRoot)) {
          extraRoot.relativize(file)
        }
        else {
          file.toString()
        }
      }
    }

    private fun shortenAndNormalizePath(file: Path?, buildPaths: BuildPaths, extraRoot: Path?): String {
      val result = shortenPath(file, buildPaths, extraRoot).replace(File.separatorChar, "/" as Char)
      return if (result.startsWith("temp/")) result.substring("temp/".length) else result
    }

    private val MAVEN_REPO = Path.of(System.getProperty("user.home"), ".m2/repository")
  }
}