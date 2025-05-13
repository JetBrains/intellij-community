// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.core.JsonFactory
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.LibraryLicense
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

fun createLibraryLicensesListGenerator(
  project: JpsProject,
  licenseList: List<LibraryLicense>,
  usedModulesNames: Set<String>,
  allowEmpty: Boolean = false,
): LibraryLicensesListGenerator {
  val licences = generateLicenses(project = project, licensesList = licenseList, usedModulesNames = usedModulesNames)
  check(allowEmpty || !licences.isEmpty()) {
    "Empty licenses table for ${licenseList.size} licenses and ${usedModulesNames.size} used modules names"
  }
  return LibraryLicensesListGenerator(licences)
}

fun getLibraryFilename(lib: JpsLibrary): String {
  val name = lib.name
  if (name.startsWith('#')) {
    // unnamed module libraries in the IntelliJ project may have only one root
    val paths = lib.getPaths(JpsOrderRootType.COMPILED)
    require(paths.size == 1) {
      "Unnamed module library has more than one element: ${paths}"
    }
    return paths[0].name
  }
  return name
}

class LibraryLicensesListGenerator internal constructor(private val libraryLicenses: List<LibraryLicense>) {
  fun generateHtml(file: Path) {
    val out = StringBuilder()
    out.append("""
     <style>
     table {
       width: 560px;
     }

     th {
       border:0pt;
       text - align: left;
     }

     td {
       padding - bottom: 11px;
     }

       .firstColumn {
         width: 410px;
         padding - left: 16px;
         padding - right: 50px;
       }

       .secondColumn {
         width: 150px;
         padding - right: 28px;
       }

       .name {
         color: #4a78c2;
         margin - right: 5px;
       }

       .version {
         color: #888888;
         line - height: 1.5em;
         white - space: nowrap;
       }

       .licence {
         color: #779dbd;
       }
     </style >
   """.trimIndent())
    out.append("\n<table>")
    out.append("\n<tr><th class=\"firstColumn\">Software</th><th class=\"secondColumn\">License</th></tr>")

    for (lib in libraryLicenses) {
      val libKey = ("${lib.presentableName}_${lib.version ?: ""}").replace(' ', '_')
      // id here is needed because of a bug IDEA-188262
      val name = if (lib.url == null) {
        "<span class=\"name\">${lib.presentableName}</span>"
      }
      else {
        "<a id=\"${libKey}_lib_url\" class=\"name\" href=\"${lib.url}\">${lib.presentableName}</a>"
      }
      val license = if (lib.getLibraryLicenseUrl() != null) {
        "<a id=\"${libKey}_license_url\" class=\"licence\" href=\"${lib.getLibraryLicenseUrl()}\">${lib.license}</a>"
      }
      else {
        "<span class=\"licence\">${lib.license}</span>"
      }
      out.append('\n')
      out.append(generateHtmlLine(name = name, libVersion = lib.version ?: "", license = license))
    }

    out.append("\n</table>")
    Files.createDirectories(file.parent)
    Files.writeString(file, out)
  }

  fun generateJson(file: Path) {
    Files.createDirectories(file.parent)
    Files.newOutputStream(file).use { out ->
      JsonFactory().createGenerator(out).useDefaultPrettyPrinter().use { writer ->
        writer.writeStartArray()
        for (entry in libraryLicenses) {
          writer.writeStartObject()

          writer.writeStringField("name", entry.presentableName)
          writer.writeStringField("url", entry.url)
          writer.writeStringField("version", entry.version)
          writer.writeStringField("license", entry.license)
          writer.writeStringField("licenseUrl", entry.getLibraryLicenseUrl())

          writer.writeEndObject()
        }
        writer.writeEndArray()
      }
    }
  }
}

private fun generateHtmlLine(name: String, libVersion: String, license: String): String {
  return """
    <tr valign="top">
      <td class="firstColumn">$name <span class="version">$libVersion</span></td>
      <td class="secondColumn">$license</td>
    </tr>
    """.trimIndent()
}

private fun generateLicenses(project: JpsProject, licensesList: List<LibraryLicense>, usedModulesNames: Set<String>): List<LibraryLicense> {
  Span.current().setAttribute(AttributeKey.stringArrayKey("modules"), usedModulesNames.toList())
  val usedModules = project.modules.filterTo(HashSet()) { usedModulesNames.contains(it.name) }
  val usedLibraries = HashMap<String, String>()
  for (module in usedModules) {
    for (item in JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries) {
      usedLibraries.put(getLibraryFilename(item), module.name)
    }
  }

  val libraryVersions = (project.libraryCollection.libraries.asSequence() +
                         project.modules.asSequence().flatMap { it.libraryCollection.libraries })
    .mapNotNull { it.asTyped(JpsRepositoryLibraryType.INSTANCE) }
    .associate { it.name to it.properties.data.version }

  val result = HashSet<LibraryLicense>()

  for (item in licensesList) {
    if (item.license == LibraryLicense.JETBRAINS_OWN) {
      continue
    }

    @Suppress("NAME_SHADOWING") var item = item
    if (item.libraryName != null && item.version == null && libraryVersions.containsKey(item.libraryName)) {
      item = LibraryLicense(name = item.name,
                            url = item.url,
                            version = libraryVersions.get(item.libraryName)!!,
                            libraryName = item.libraryName,
                            additionalLibraryNames = item.additionalLibraryNames,
                            attachedTo = item.attachedTo,
                            transitiveDependency = item.transitiveDependency,
                            license = item.license,
                            licenseUrl = item.licenseUrl)
    }

    if (usedModulesNames.contains(item.attachedTo)) {
      // item.attachedTo
      result.add(item)
    }
    else {
      for (name in item.getLibraryNames()) {
        if (usedLibraries.containsKey(name)) {
          result.add(item)
        }
      }
    }
  }
  return result.sortedBy { it.presentableName }
}

