// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import groovy.text.SimpleTemplateEngine
import groovy.transform.CompileStatic
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.LibraryLicense
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function

@CompileStatic
final class LibraryLicensesListGenerator {
  private final List<LibraryLicense> libraryLicenses

  private LibraryLicensesListGenerator(List<LibraryLicense> libraryLicenses) {
    this.libraryLicenses = libraryLicenses
  }

  static LibraryLicensesListGenerator create(JpsProject project,
                                             List<LibraryLicense> licensesList,
                                             Set<String> usedModulesNames) {
    List<LibraryLicense> licences = generateLicenses(project, licensesList, usedModulesNames)
    if (licences.isEmpty()) {
      throw new IllegalStateException("Empty licenses table for ${licensesList.size()} licenses and ${usedModulesNames.size()} used modules names")
    }
    return new LibraryLicensesListGenerator(licences)
  }

  private static List<LibraryLicense> generateLicenses(JpsProject project,
                                                       List<LibraryLicense> licensesList,
                                                       Set<String> usedModulesNames) {
    Span.current().setAttribute(AttributeKey.stringArrayKey("modules"), List.copyOf(usedModulesNames))
    Set<JpsModule> usedModules = project.modules.findAll { usedModulesNames.contains(it.name) } as Set<JpsModule>
    Map<String, String> usedLibraries = new HashMap<>()
    for (JpsModule module in usedModules) {
      JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).getLibraries().each { item ->
        usedLibraries.put(getLibraryName(item), module.name)
      }
    }

    List<JpsLibrary> moduleLibraries = project.modules.collectMany { it.libraryCollection.libraries }
    Map<String, String> libraryVersions = (project.libraryCollection.libraries + moduleLibraries)
      .collect { it.asTyped(JpsRepositoryLibraryType.INSTANCE) }
      .findAll { it != null}
      .collectEntries { [it.name, it.properties.data.version] }

    List<LibraryLicense> licenses = new ArrayList<>()

    licensesList.findAll { it.license != LibraryLicense.JETBRAINS_OWN }.each { LibraryLicense lib ->
      if (lib.libraryName != null && lib.version == null && libraryVersions.containsKey(lib.libraryName)) {
        lib = new LibraryLicense(lib.name, lib.url, libraryVersions[lib.libraryName], lib.libraryName, lib.additionalLibraryNames,
                                 lib.attachedTo, lib.transitiveDependency, lib.license, lib.licenseUrl)
      }
      if (usedModulesNames.contains(lib.attachedTo)) {
        // lib.attachedTo
        licenses.add(lib)
      }
      else {
        for (String name in lib.libraryNames) {
          String module = usedLibraries.get(name)
          if (module != null) {
            licenses.add(lib)
          }
        }
      }
    }

    licenses.sort(Comparator.comparing(new Function<LibraryLicense, String>() {
      @Override
      String apply(LibraryLicense library) {
        return library.presentableName
      }
    }))
    return licenses
  }

  static String getLibraryName(JpsLibrary lib) {
    def name = lib.name
    if (name.startsWith("#")) {
      //unnamed module libraries in IntelliJ project may have only one root
      return lib.getFiles(JpsOrderRootType.COMPILED).first().name
    }
    return name
  }

  void generateHtml(Path file) {
    String line = '''
  <tr valign="top">
    <td class="firstColumn">
      $name
      <span class="version">$libVersion</span>
    </td>
    <td class="secondColumn">
      $license
    </td>
  </tr>
      '''.trim()
    SimpleTemplateEngine engine = new SimpleTemplateEngine()

    StringBuilder out = new StringBuilder()
    out.append('''
<style>
  table {
    width: 560px;
  }
  
  th {
    border:0pt;
    text-align: left;
  }
  
  td {
    padding-bottom: 11px;
  }
  
  .firstColumn {
    width: 410px;
    padding-left: 16px;
    padding-right: 50px;
  }
  
  .secondColumn {
    width: 150px;
    padding-right: 28px;
  }
  
  .name {
    color: #4a78c2;
    margin-right: 5px;
  }
    
  .version {
    color: #888888;
    line-height: 1.5em;
    white-space: nowrap;
  }
  
  .licence {
    color: #779dbd;
  }
</style>
'''.trim())
    out.append("\n<table>")
    out.append("\n<tr><th class=\"firstColumn\">Software</th><th class=\"secondColumn\">License</th></tr>")

    for (LibraryLicense lib in libraryLicenses) {
      String libKey = (lib.presentableName + "_" + lib.version ?: "").replace(" ", "_")
      // id here is needed because of a bug IDEA-188262
      String name = lib.url != null ? "<a id=\"${libKey}_lib_url\" class=\"name\" href=\"$lib.url\">$lib.presentableName</a>" :
                    "<span class=\"name\">$lib.presentableName</span>"
      String license = lib.libraryLicenseUrl != null ?
                       "<a id=\"${libKey}_license_url\" class=\"licence\" href=\"$lib.libraryLicenseUrl\">$lib.license</a>" :
                       "<span class=\"licence\">$lib.license</span>"
      out.append('\n' as char)
      engine.createTemplate(line).make(["name": name, "libVersion": lib.version ?: "", "license": license]).writeTo(new Writer() {
        @Override
        void write(@NotNull char[] chars, int off, int len) throws IOException {
          out.append(chars, off, len)
        }

        @Override
        void flush() throws IOException {
        }

        @Override
        void close() throws IOException {
        }
      })
    }

    out.append("\n</table>")
    Files.createDirectories(file.getParent())
    Files.writeString(file, out)
  }

  void generateJson(Path file) {
    Files.createDirectories(file.getParent())
    Files.newOutputStream(file).withCloseable { out ->
      JsonGenerator writer = new JsonFactory().createGenerator(out).useDefaultPrettyPrinter()
      writer.writeStartArray()
      for (LibraryLicense entry : libraryLicenses) {
        writer.writeStartObject()

        writer.writeStringField("name", entry.presentableName)
        writer.writeStringField("url", entry.url)
        writer.writeStringField("version", entry.version)
        writer.writeStringField("license", entry.license)
        writer.writeStringField("licenseUrl", entry.libraryLicenseUrl)

        writer.writeEndObject()
      }
      writer.writeEndArray()
      writer.close()
    }
  }
}

