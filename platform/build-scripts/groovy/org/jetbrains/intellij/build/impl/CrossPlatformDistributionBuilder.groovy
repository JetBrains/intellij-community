// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.io.FileType
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

import java.util.regex.Pattern

/**
 * @author nik
 */
class CrossPlatformDistributionBuilder {
  private final BuildContext buildContext

  CrossPlatformDistributionBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  String buildCrossPlatformZip(String winDistPath, String linuxDistPath, String macDistPath, String zipNameSuffix) {
    buildContext.messages.block("Building cross-platform zip") {
      def executableName = buildContext.productProperties.baseFileName
      def zipDir = "$buildContext.paths.temp/cross-platform-zip"
      buildContext.ant.copy(todir: "$zipDir/bin/win") {
        fileset(dir: "$winDistPath/bin") {
          include(name: "idea.properties")
        }
      }
      buildContext.ant.copy(todir: "$zipDir/bin/linux") {
        fileset(dir: "$linuxDistPath/bin") {
          include(name: "*.vmoptions")
          include(name: "idea.properties")
        }
      }
      buildContext.ant.copy(todir: "$zipDir/bin/mac") {
        fileset(dir: "$macDistPath/bin") {
          include(name: "${executableName}.vmoptions")
          include(name: "idea.properties")
        }
      }
      buildContext.ant.copy(file: "$macDistPath/bin/${executableName}.vmoptions", tofile: "$zipDir/bin/mac/${executableName}64.vmoptions")
      buildContext.ant.copy(todir: "$zipDir/bin") {
        fileset(dir: "$macDistPath/bin") {
          include(name: "*.jnilib")
          exclude(name: ".DS_Store")
        }
        mapper(type: "glob", from: "*.jnilib", to: "*.dylib")
      }

      Map<String, File> linuxFiles = collectFilesUnder(linuxDistPath)
      Map<String, File> macFiles = collectFilesUnder(macDistPath)
      def commonFiles = checkCommonFilesAreTheSame(linuxFiles, macFiles)

      new ProductInfoGenerator(buildContext).generateMultiPlatformProductJson(zipDir, "bin", [
        new ProductInfoLaunchData(OsFamily.WINDOWS.osName, "bin/${executableName}.bat", null, "bin/win/${executableName}64.exe.vmoptions",
                                  null),
        new ProductInfoLaunchData(OsFamily.LINUX.osName, "bin/${executableName}.sh", null, "bin/linux/${executableName}64.vmoptions",
                                  LinuxDistributionBuilder.getFrameClass(buildContext)),
        new ProductInfoLaunchData(OsFamily.MACOS.osName, "MacOS/$executableName", null, "bin/mac/${executableName}.vmoptions", null)
      ])

      String baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
      String jreSuffix = buildContext.bundledJreManager.jreSuffix()
      String targetPath = "$buildContext.paths.artifacts/${baseName}${zipNameSuffix}${jreSuffix}.zip"

      buildContext.ant.zip(zipfile: targetPath, duplicate: "fail") {
        fileset(dir: buildContext.paths.distAll) {
          exclude(name: "bin/idea.properties")
        }
        fileset(dir: zipDir)
        fileset(file: "$buildContext.paths.artifacts/dependencies.txt")

        fileset(dir: winDistPath) {
          exclude(name: "bin/fsnotifier*.exe")
          exclude(name: "bin/*.exe.vmoptions")
          exclude(name: "bin/${executableName}*.exe")
          exclude(name: "bin/idea.properties")
          exclude(name: "help/**")
        }
        zipfileset(dir: "$winDistPath/bin", prefix: "bin/win") {
          include(name: "fsnotifier*.exe")
          include(name: "*.exe.vmoptions")
        }

        fileset(dir: linuxDistPath) {
          exclude(name: "bin/fsnotifier*")
          exclude(name: "bin/*.vmoptions")
          exclude(name: "bin/*.sh")
          exclude(name: "bin/*.py")
          exclude(name: "bin/idea.properties")
          exclude(name: "help/**")

          buildContext.linuxDistributionCustomizer.extraExecutables.each {
            exclude(name: it)
          }
        }
        if (!buildContext.linuxDistributionCustomizer.extraExecutables.isEmpty()) {
          zipfileset(dir: "$linuxDistPath", filemode: "775") {
            buildContext.linuxDistributionCustomizer.extraExecutables.each {
              include(name: it)
            }
          }
        }
        zipfileset(dir: "$linuxDistPath/bin", prefix: "bin", filemode: "775") {
          include(name: "*.sh")
          include(name: "*.py")
        }
        zipfileset(dir: "$linuxDistPath/bin", prefix: "bin/linux", filemode: "775") {
          include(name: "fsnotifier*")
        }

        fileset(dir: macDistPath) {
          exclude(name: "bin/fsnotifier*")
          exclude(name: "bin/restarter*")
          exclude(name: "bin/*.sh")
          exclude(name: "bin/*.py")
          exclude(name: "bin/*.jnilib")
          exclude(name: "bin/idea.properties")
          exclude(name: "bin/*.vmoptions")

          commonFiles.each {
            exclude(name: it)
          }

          buildContext.macDistributionCustomizer.extraExecutables.each {
            exclude(name: it)
          }

          if (buildContext.macDistributionCustomizer.helpId) {
            exclude(name: "Resources/${buildContext.macDistributionCustomizer.helpId}.help/**")
          }
        }
        if (!buildContext.macDistributionCustomizer.extraExecutables.isEmpty()) {
          zipfileset(dir: "$macDistPath", filemode: "775") {
            buildContext.macDistributionCustomizer.extraExecutables.each {
              include(name: it)
            }

            commonFiles.each {
              exclude(name: it)
            }
          }
        }
        zipfileset(dir: "$macDistPath/bin", prefix: "bin", filemode: "775") {
          include(name: "restarter*")
        }
        zipfileset(dir: "$macDistPath/bin", prefix: "bin/mac", filemode: "775") {
          include(name: "fsnotifier*")
        }
      }
      new ProductInfoValidator(buildContext).checkInArchive(targetPath, "")
      buildContext.notifyArtifactBuilt(targetPath)

      targetPath
    }
  }

  private checkCommonFilesAreTheSame(Map<String, File> linuxFiles, Map<String, File> macFiles) {
    def commonFiles = linuxFiles.keySet().intersect(macFiles.keySet() as Iterable<String>)

    def knownExceptions = [
            "bin/idea\\.properties",
            "bin/printenv\\.py",
            "bin/\\w+\\.vmoptions",
            "bin/format\\.sh",
            "bin/inspect\\.sh",
            "bin/fsnotifier",
    ]

    def violations = new ArrayList<String>()
    for (String commonFile : commonFiles) {
      def linuxFile = linuxFiles[commonFile]
      def macFile = macFiles[commonFile]

      if (linuxFile.readBytes() != macFile.readBytes()) {
        if (knownExceptions.any { Pattern.matches(it, commonFile) }) {
          continue
        }

        violations.add("$commonFile: $linuxFile and $macFile")
      }
    }

    if (!violations.isEmpty()) {
      buildContext.messages.error(
              "Files are at the same path in linux and mac distribution, " +
                      "but have a different content in each. Please place them at different paths. " +
                      "Files:\n" + violations.join("\n")
      )
    }

    return commonFiles
  }

  private static Map<String, File> collectFilesUnder(String rootPath) {
    def root = new File(rootPath)
    Map<String, File> result = [:]
    root.eachFileRecurse(FileType.FILES) {
      def relativePath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(root, it))
      result[relativePath] = it
    }
    return result
  }
}