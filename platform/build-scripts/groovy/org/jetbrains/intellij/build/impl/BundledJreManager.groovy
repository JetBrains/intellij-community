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

import com.intellij.openapi.util.SystemInfo
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture

/**
 * @author nik
 */
@CompileStatic
class BundledJreManager {
  private final BuildContext buildContext
  String baseDirectoryForJre

  BundledJreManager(BuildContext buildContext, String baseDirectoryForJre) {
    this.buildContext = buildContext
    this.baseDirectoryForJre = baseDirectoryForJre
  }

  /**
   * Extract JRE for Linux distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String extractLinuxJre() {
    return extractJre("linux")
  }

  /**
   * Extract JRE for Windows distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String extractWinJre(JvmArchitecture arch) {
    return extractJre("win", arch)
  }

  /**
   * Extract Oracle JRE for Windows distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String extractOracleWinJre(JvmArchitecture arch) {
    return extractJre("win", arch, JreVendor.Oracle)
  }

  /**
   * Return path to a .tar.gz archive containing distribution of JRE for Mac OS which will be bundled with the product
   */
  String findMacJreArchive() {
    return findJreArchive("mac")?.absolutePath
  }

  @CompileDynamic
  private String extractJre(String osDirName, JvmArchitecture arch = JvmArchitecture.x64, JreVendor vendor = JreVendor.JetBrains) {
    String vendorSuffix = vendor == JreVendor.Oracle ? ".oracle" : ""
    String targetDir = "$baseDirectoryForJre/jre.$osDirName$arch.fileSuffix$vendorSuffix"
    if (new File(targetDir).exists()) {
      buildContext.messages.info("JRE is already extracted to $targetDir")
      return targetDir
    }

    File archive = findJreArchive(osDirName, arch, vendor)
    if (archive == null) {
      return null
    }
    buildContext.messages.block("Extract $archive.name JRE") {
      String destination = "$targetDir/jre"
      buildContext.messages.progress("Extracting JRE from '$archive.name' archive")
      if (SystemInfo.isWindows) {
        buildContext.ant.untar(src: archive.absolutePath, dest: destination, compression: 'gzip')
      }
      else {
        //'tar' command is used instead of Ant task to ensure that executable flags will be preserved
        buildContext.ant.mkdir(dir: destination)
        buildContext.ant.exec(executable: "tar", dir: archive.parent) {
          arg(value: "-xf")
          arg(value: archive.name)
          arg(value: "--directory")
          arg(value: destination)
        }
      }
    }
    return targetDir
  }

  private File findJreArchive(String osDirName, JvmArchitecture arch = JvmArchitecture.x64, JreVendor vendor = JreVendor.JetBrains) {
    def jdkDir = new File(buildContext.paths.projectHome, "build/jdk/$osDirName")
    String suffix = arch == JvmArchitecture.x32 ? "_x86" : "_x64"
    String prefix = buildContext.productProperties.toolsJarRequired ? vendor.jreWithToolsJarNamePrefix : vendor.jreNamePrefix
    Collection<File> jdkFiles = jdkDir.listFiles()?.findAll { it.name.startsWith(prefix) && it.name.endsWith("${suffix}.tar.gz") } ?: [] as List<File>
    if (jdkFiles.size() > 1) {
      buildContext.messages.warning("Cannot extract $osDirName JRE: several matching files are found ($jdkFiles)")
      return null
    }
    if (jdkFiles.isEmpty()) {
      buildContext.messages.warning("Cannot extract $osDirName JRE: no '${prefix}...${suffix}.tar.gz' files found in $jdkDir")
      return null
    }
    return jdkFiles.first()
  }

  private enum JreVendor {
    Oracle("jre8", "jdk8"), JetBrains("jbre8", "jbrex8")

    final String jreNamePrefix
    final String jreWithToolsJarNamePrefix

    JreVendor(String jreNamePrefix, String jreWithToolsJarNamePrefix) {
      this.jreNamePrefix = jreNamePrefix
      this.jreWithToolsJarNamePrefix = jreWithToolsJarNamePrefix
    }
  }
}