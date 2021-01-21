// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.google.gson.GsonBuilder
import groovy.text.SimpleTemplateEngine
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
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

@CompileStatic
final class LibraryLicensesListGenerator {
  private final BuildMessages messages
  private Map<LibraryLicense, String> licensesInModules

  private LibraryLicensesListGenerator(BuildMessages messages,
                                       Map<LibraryLicense, String> licensesInModules) {
    this.messages = messages
    this.licensesInModules = licensesInModules
  }

  static LibraryLicensesListGenerator create(BuildMessages messages,
                                             JpsProject project,
                                             List<LibraryLicense> licensesList,
                                             Set<String> usedModulesNames) {
    Map<LibraryLicense, String> licences = generateLicenses(messages, project, licensesList, usedModulesNames)
    if (licences.isEmpty()) {
      messages.error("Empty licenses table for ${licensesList.size()} licenses and ${usedModulesNames.size()} used modules names")
    }
    return new LibraryLicensesListGenerator(messages, licences)
  }

  private static Map<LibraryLicense, String> generateLicenses(BuildMessages messages,
                                                              JpsProject project,
                                                              List<LibraryLicense> licensesList,
                                                              Set<String> usedModulesNames) {
    Map<LibraryLicense, String> licenses = [:]
    messages.debug("Generating licenses table")
    messages.debug("Used modules: $usedModulesNames")
    Set<JpsModule> usedModules = project.modules.findAll { usedModulesNames.contains(it.name) } as Set<JpsModule>
    Map<String, String> usedLibraries = [:]
    usedModules.each { JpsModule module ->
      JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).getLibraries().each { item ->
        def libraryName = getLibraryName(item)
        usedLibraries[libraryName] = module.name
      }
    }
    Map<String, String> libraryVersions = (project.libraryCollection.libraries + project.modules.collectMany {it.libraryCollection.libraries})
      .collect { it.asTyped(JpsRepositoryLibraryType.INSTANCE) }
      .findAll { it != null}
      .collectEntries { [it.name, it.properties.data.version] }

    licensesList.findAll { it.license != LibraryLicense.JETBRAINS_OWN }.each { LibraryLicense lib ->
      if (lib.libraryName != null && lib.version == null && libraryVersions.containsKey(lib.libraryName)) {
        lib = new LibraryLicense(lib.name, lib.url, libraryVersions[lib.libraryName], lib.libraryName, lib.additionalLibraryNames,
                                 lib.attachedTo, lib.transitiveDependency, lib.license, lib.licenseUrl)
      }
      if (usedModulesNames.contains(lib.attachedTo)) {
        licenses[lib] = lib.attachedTo
      }
      else {
        lib.libraryNames.each {
          String module = usedLibraries[it]
          if (module != null) {
            licenses[lib] = module
          }
        }
      }
    }
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
    messages.debug("Used libraries:")

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

    List<String> lines = new ArrayList<>()
    for (entry in licensesInModules.entrySet()) {
      LibraryLicense lib = entry.key
      String moduleName = entry.value

      String libKey = (lib.presentableName + "_" + lib.version ?: "").replace(" ", "_")
      // id here is needed because of a bug IDEA-188262
      String name = lib.url != null ? "<a id=\"${libKey}_lib_url\" class=\"name\" href=\"$lib.url\">$lib.presentableName</a>" :
                    "<span class=\"name\">$lib.presentableName</span>"
      String license = lib.libraryLicenseUrl != null ?
                       "<a id=\"${libKey}_license_url\" class=\"licence\" href=\"$lib.libraryLicenseUrl\">$lib.license</a>" :
                       "<span class=\"licence\">$lib.license</span>"

      messages.debug(" $lib.presentableName (in module $moduleName)")
      lines.add(engine.createTemplate(line).make(["name": name, "libVersion": lib.version ?: "", "license": license]).toString())
    }

    lines.sort(true, String.CASE_INSENSITIVE_ORDER)
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
    for (it in lines) {
      out.append('\n')
      out.append(it)
    }
    out.append("\n</table>")
    Files.createDirectories(file.getParent())
    Files.writeString(file, out)
  }

  void generateJson(Path file) {
    List<LibraryLicenseData> entries = []

    licensesInModules.keySet().sort( {it.presentableName} ).each {
      entries.add(
        new LibraryLicenseData(
          name: it.presentableName,
          url: it.url,
          version: it.version,
          license: it.license,
          licenseUrl: it.libraryLicenseUrl
        )
      )
    }

    Files.createDirectories(file.getParent())
    Files.newBufferedWriter(file).withCloseable {
      new GsonBuilder().setPrettyPrinting().create().toJson(entries, it)
    }
  }
}

