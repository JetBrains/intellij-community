/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.LibraryLicense
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
/**
 * @author nik
 */
@CompileStatic
class LibraryLicensesListGenerator {
  private final BuildMessages messages
  private final JpsProject project
  private final List<LibraryLicense> licensesList

  LibraryLicensesListGenerator(BuildMessages messages, JpsProject project, List<LibraryLicense> licensesList) {
    this.messages = messages
    this.project = project
    this.licensesList = licensesList
  }

  static String getLibraryName(JpsLibrary lib) {
    def name = lib.name
    if (name.startsWith("#")) {
      File file = lib.getFiles(JpsOrderRootType.COMPILED)[0]
      return file.name
    }
    return name
  }

  void generateLicensesTable(String filePath, Set<String> usedModulesNames) {
    messages.info("Generating licenses table")
    messages.info("Used modules: $usedModulesNames")
    Set<JpsModule> usedModules = project.modules.findAll { usedModulesNames.contains(it.name) } as Set<JpsModule>
    Map<String, String> usedLibraries = [:]
    usedModules.each { JpsModule module ->
      JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).getLibraries().each { item ->
        usedLibraries[getLibraryName(item)] = module.name
      }
    }

    Map<LibraryLicense, String> licenses = [:]
    licensesList.findAll {it.license != LibraryLicense.JETBRAINS_OWN}.each { LibraryLicense lib ->
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

    messages.info("Used libraries:")
    List<String> lines = []
    licenses.entrySet().each {
      LibraryLicense lib = it.key
      String moduleName = it.value
      def name = lib.url != null ? "[$lib.name|$lib.url]" : lib.name
      def license = lib.libraryLicenseUrl != null ? "[$lib.license|$lib.libraryLicenseUrl]" : lib.license
      messages.info(" $lib.name (in module $moduleName)")
      lines << "|$name| ${lib.version ?: ""}|$license|".toString()
    }
    //projectBuilder.info("Unused libraries:")
    //licensesList.findAll {!licenses.containsKey(it)}.each {LibraryLicense lib ->
    //  projectBuilder.info(" $lib.name")
    //}

    lines.sort(true, String.CASE_INSENSITIVE_ORDER)
    File file = new File(filePath)
    file.parentFile.mkdirs()
    FileWriter out = new FileWriter(file)
    try {
      out.println("|| Software || Version || License ||")
      lines.each {
        out.println(it)
      }
    }
    finally {
      out.close()
    }
  }
}

