// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import groovy.io.FileType
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

@CompileStatic
final class CrossPlatformDistributionBuilder {
  private final BuildContext buildContext

  CrossPlatformDistributionBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  String buildCrossPlatformZip(Path winDistPath, Path linuxDistPath, Path macDistPath) {
    buildContext.messages.block("Building cross-platform zip") {
      def executableName = buildContext.productProperties.baseFileName
      Path zipDir = Paths.get(buildContext.paths.temp, "cross-platform-zip")
      Files.createDirectories(zipDir)
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
      Files.copy(macDistPath.resolve("bin/${executableName}.vmoptions"), zipDir.resolve("bin/mac/${executableName}64.vmoptions"), StandardCopyOption.REPLACE_EXISTING)
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

      def zipFileName = buildContext.productProperties.getCrossPlatformZipFileName(buildContext.applicationInfo, buildContext.buildNumber)
      String targetPath = "$buildContext.paths.artifacts/$zipFileName"

      List<String> extraExecutables = buildContext.linuxDistributionCustomizer.extraExecutables + buildContext.macDistributionCustomizer.extraExecutables
      buildContext.ant.zip(zipfile: targetPath, duplicate: "fail") {
        fileset(dir: buildContext.paths.distAll) {
          exclude(name: "bin/idea.properties")

          if (linuxFiles.containsKey("lib/classpath.txt")) { //linux has extra dbus-java
            exclude(name: "lib/classpath.txt")
          }

          extraExecutables.each {
            exclude(name: it)
          }
        }

        if (!extraExecutables.isEmpty()) {
          zipfileset(dir: buildContext.paths.distAll, filemode: "775") {
            extraExecutables.each {
              include(name: it)
            }
          }
        }

        fileset(dir: zipDir)
        fileset(file: "$buildContext.paths.artifacts/dependencies.txt")

        fileset(dir: winDistPath) {
          exclude(name: "bin/fsnotifier*.exe")
          exclude(name: "bin/*.exe.vmoptions")
          exclude(name: "bin/${executableName}*.exe")
          exclude(name: "bin/idea.properties")
          exclude(name: "help/**")
          exclude(name: "build.txt")
          buildContext.distFiles.each {
            exclude(name: it.second + "/" + it.first.fileName.toString())
          }
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
          exclude(name: "bin/printenv*")
          exclude(name: "bin/*.sh")
          exclude(name: "bin/idea.properties")
          exclude(name: "bin/*.vmoptions")

          commonFiles.each {
            exclude(name: it)
          }

          buildContext.macDistributionCustomizer.extraExecutables.each {
            exclude(name: it)
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
          include(name: "printenv*")
        }
        zipfileset(dir: "$macDistPath/bin", prefix: "bin/mac", filemode: "775") {
          include(name: "fsnotifier*")
        }
      }
      ProductInfoValidator.checkInArchive(buildContext, targetPath, "")
      buildContext.notifyArtifactBuilt(targetPath)

      targetPath
    }
  }

  private checkCommonFilesAreTheSame(Map<String, File> linuxFiles, Map<String, File> macFiles) {
    def commonFiles = linuxFiles.keySet().intersect(macFiles.keySet() as Iterable<String>)

    List<String> knownExceptions = List.of(
      "bin/idea\\.properties",
      "bin/\\w+\\.vmoptions",
      "bin/format\\.sh",
      "bin/inspect\\.sh",
      "bin/ltedit\\.sh",
      "bin/fsnotifier",
    )

    List<String> violations = new ArrayList<String>()
    for (String commonFile : commonFiles) {
      def linuxFile = linuxFiles[commonFile]
      def macFile = macFiles[commonFile]

      if (linuxFile.readBytes() != macFile.readBytes()) {
        if (knownExceptions.any { Pattern.matches(it, commonFile) }) {
          continue
        }

        violations.add("$commonFile: ${linuxFile.toString()} and $macFile" as String)
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

  private static Map<String, File> collectFilesUnder(@NotNull Path rootPath) {
    File root = rootPath.toFile()
    Map<String, File> result = new HashMap<>()
    root.eachFileRecurse(FileType.FILES) {
      def relativePath = FileUtilRt.toSystemIndependentName(FileUtilRt.getRelativePath(root, it))
      result[relativePath] = it
    }
    return result
  }
}
