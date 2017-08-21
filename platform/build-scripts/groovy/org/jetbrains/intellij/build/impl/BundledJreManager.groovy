/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
    return extractJre("windows", arch)
  }

  /**
   * Extract Oracle JRE for Windows distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String extractOracleWinJre(JvmArchitecture arch) {
    return extractJre("windows", arch, JreVendor.Oracle)
  }

  /**
   * Return path to a .tar.gz archive containing distribution of JRE for macOS which will be bundled with the product
   */
  String findMacJreArchive() {
    return findJreArchive("osx")?.absolutePath
  }

  /**
   * Return a .tar.gz archive containing distribution of JRE for Win OS which will be bundled with the product
   */
  File findWinJreArchive(JvmArchitecture arch) {
    return findJreArchive("windows", arch)
  }

  String archiveNameJre(BuildContext buildContext) {
    return "jre-for-${buildContext.buildNumber}.tar.gz"
  }


  @CompileDynamic
  private String extractJre(String osName, JvmArchitecture arch = JvmArchitecture.x64, JreVendor vendor = JreVendor.JetBrains) {
    String vendorSuffix = vendor == JreVendor.Oracle ? ".oracle" : ""
    String targetDir = arch == JvmArchitecture.x64 ?
                       "$baseDirectoryForJre/jre.$osName$arch.fileSuffix$vendorSuffix" :
                       "$baseDirectoryForJre/jre.${osName}32$vendorSuffix"
    if (new File(targetDir).exists()) {
      buildContext.messages.info("JRE is already extracted to $targetDir")
      return targetDir
    }

    File archive = findJreArchive(osName, arch, vendor)
    if (archive == null) {
      return null
    }
    buildContext.messages.block("Extract $archive.name JRE") {
      String destination = "$targetDir/jre64"
      if (osName == "windows" && arch == JvmArchitecture.x32) {
        destination = "$targetDir/jre32"
      }
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
          arg(value: "--strip")
          arg(value: "1")
          arg(value: "--directory")
          arg(value: destination)
        }
      }
    }
    return targetDir
  }

  private File findJreArchive(String osName, JvmArchitecture arch = JvmArchitecture.x64, JreVendor vendor = JreVendor.JetBrains) {
    def dependenciesDir = new File(buildContext.paths.communityHome, 'build/dependencies')
    def jreDir = new File(dependenciesDir, 'build/jbre')
    def jreVersion = getExpectedJreVersion(osName, dependenciesDir)

    String suffix = "${jreVersion}_$osName${arch == JvmArchitecture.x32 ? '_x86' : '_x64'}.tar.gz"
    String prefix = buildContext.productProperties.toolsJarRequired ? vendor.jreWithToolsJarNamePrefix : vendor.jreNamePrefix
    def jreArchive = new File(jreDir, "$prefix$suffix")

    if (!jreArchive.file || !jreArchive.exists()) {
      def errorMessage = "Cannot extract $osName JRE: file $jreArchive is not found (${jreDir.listFiles()})"
      if (buildContext.options.isInDevelopmentMode) {
        buildContext.messages.warning(errorMessage)
      }
      else {
        buildContext.messages.error(errorMessage)
      }
      return null
    }
    return jreArchive
  }

  private Properties dependencyVersions
  private String getExpectedJreVersion(String osName, File dependenciesDir) {
    if (dependencyVersions == null) {
      buildContext.gradle.run('Preparing dependencies file', 'dependenciesFile')

      def stream = new File(dependenciesDir, 'build/dependencies.properties').newInputStream()
      try {
        Properties properties = new Properties()
        properties.load(stream)
        dependencyVersions = properties
      }
      finally {
        stream.close()
      }
    }
    return dependencyVersions.get("jreBuild_${osName}" as String, dependencyVersions.get("jdkBuild", ""))
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