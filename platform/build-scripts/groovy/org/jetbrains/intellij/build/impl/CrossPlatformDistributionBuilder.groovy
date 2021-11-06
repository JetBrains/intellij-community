// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinTask
import java.util.regex.Pattern

@CompileStatic
final class CrossPlatformDistributionBuilder {
  @CompileStatic(TypeCheckingMode.SKIP)
  static Path buildCrossPlatformZip(Path winDistPath, Path linuxDistPath, Path macDistPath, BuildContext context) {
    ForkJoinTask<Map<String, Path>> macFiles = ForkJoinTask.adapt(new Callable<Map<String, Path>>() {
      @Override
      Map<String, Path> call() throws Exception {
        return collectFilesUnder(macDistPath)
      }
    }).fork()
    ForkJoinTask<Map<String, Path>> linuxFiles = ForkJoinTask.adapt(new Callable<Map<String, Path>>() {
      @Override
      Map<String, Path> call() throws Exception {
        return collectFilesUnder(linuxDistPath)
      }
    }).fork()

    String executableName = context.productProperties.baseFileName

    byte[] productJson = new ProductInfoGenerator(context).generateMultiPlatformProductJson("bin", List.of(
      new ProductInfoLaunchData(OsFamily.WINDOWS.osName, "bin/${executableName}.bat", null, "bin/win/${executableName}64.exe.vmoptions",
                                null),
      new ProductInfoLaunchData(OsFamily.LINUX.osName, "bin/${executableName}.sh", null, "bin/linux/${executableName}64.vmoptions",
                                LinuxDistributionBuilder.getFrameClass(context)),
      new ProductInfoLaunchData(OsFamily.MACOS.osName, "MacOS/$executableName", null, "bin/mac/${executableName}.vmoptions", null)
    ))

    String zipFileName = context.productProperties.getCrossPlatformZipFileName(context.applicationInfo, context.buildNumber)
    Path targetFile = Path.of("$context.paths.artifacts/$zipFileName")

    List<String> extraExecutables =
      context.linuxDistributionCustomizer.extraExecutables + context.macDistributionCustomizer.extraExecutables

    BuildHelper.getInstance(context).crossPlatformArchive.invokeWithArguments(
      winDistPath, linuxDistPath, macDistPath,
      targetFile,
      executableName,
      productJson, extraExecutables,
      context.paths.distAllDir,
      )

    Set<String> commonFiles = checkCommonFilesAreTheSame(linuxFiles.join(), macFiles.join(), context)

    context.ant.zip(zipfile: targetFile.toString(), duplicate: "fail", update: true) {
      fileset(dir: context.paths.distAll) {
        exclude(name: "bin/idea.properties")

        if (linuxFiles.rawResult.containsKey("lib/classpath.txt")) { //linux has extra dbus-java
          exclude(name: "lib/classpath.txt")
        }

        extraExecutables.each {
          exclude(name: it)
        }
      }

      fileset(file: "$context.paths.artifacts/dependencies.txt")

      fileset(dir: winDistPath) {
        exclude(name: "bin/fsnotifier*.exe")
        exclude(name: "bin/*.exe.vmoptions")
        exclude(name: "bin/${executableName}*.exe")
        exclude(name: "bin/idea.properties")
        exclude(name: "help/**")
        exclude(name: "build.txt")
        context.distFiles.each {
          exclude(name: it.value + "/" + it.key.fileName.toString())
        }
      }

      fileset(dir: linuxDistPath) {
        exclude(name: "bin/fsnotifier*")
        exclude(name: "bin/*.vmoptions")
        exclude(name: "bin/*.sh")
        exclude(name: "bin/*.py")
        exclude(name: "bin/idea.properties")
        exclude(name: "help/**")

        context.linuxDistributionCustomizer.extraExecutables.each {
          exclude(name: it)
        }
      }
      if (!context.linuxDistributionCustomizer.extraExecutables.isEmpty()) {
        zipfileset(dir: "$linuxDistPath", filemode: "775") {
          context.linuxDistributionCustomizer.extraExecutables.each {
            include(name: it)
          }
        }
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

        context.macDistributionCustomizer.extraExecutables.each {
          exclude(name: it)
        }
      }
      if (!context.macDistributionCustomizer.extraExecutables.isEmpty()) {
        zipfileset(dir: "$macDistPath", filemode: "775") {
          context.macDistributionCustomizer.extraExecutables.each {
            include(name: it)
          }

          commonFiles.each {
            exclude(name: it)
          }
        }
      }
    }
    ProductInfoValidator.checkInArchive(context, targetFile, "")
    context.notifyArtifactBuilt(targetFile)
    return targetFile
  }

  private static final int bufferSize = 32 * 1024

  private static Set<String> checkCommonFilesAreTheSame(Map<String, Path> linuxFiles, Map<String, Path> macFiles, BuildContext context) {
    Set<String> commonFiles = linuxFiles.keySet().intersect(macFiles.keySet() as Iterable<String>)

    List<Pattern> knownExceptions = List.of(
      "bin/idea\\.properties",
      "bin/\\w+\\.vmoptions",
      "bin/format\\.sh",
      "bin/inspect\\.sh",
      "bin/ltedit\\.sh",
      "bin/fsnotifier",
    ).collect { Pattern.compile(it) }

    List<String> violations = new ArrayList<String>()

    byte[] buffer1 = new byte[bufferSize]
    byte[] buffer2 = new byte[bufferSize]

    for (String commonFile : commonFiles) {
      Path linuxFile = linuxFiles.get(commonFile)
      Path macFile = macFiles.get(commonFile)
      if (!contentEquals(linuxFile, macFile, buffer1, buffer2)) {
        if (knownExceptions.any { it.matcher(commonFile).matches() }) {
          continue
        }

        violations.add("$commonFile: ${linuxFile.toString()} and $macFile" as String)
      }
    }

    if (!violations.isEmpty()) {
      context.messages.error(
        "Files are at the same path in linux and mac distribution, " +
        "but have a different content in each. Please place them at different paths. " +
        "Files:\n" + String.join("\n", violations)
      )
    }

    return commonFiles
  }

  // since JDK 12+ - Files.mismatch
  private static boolean contentEquals(Path path, Path path2, byte[] buffer1, byte[] buffer2) throws IOException {
    Files.newInputStream(path).withCloseable { in1 ->
      Files.newInputStream(path2).withCloseable { in2 ->
        long totalRead = 0
        while (true) {
          int nRead1 = in1.readNBytes(buffer1, 0, bufferSize)
          int nRead2 = in2.readNBytes(buffer2, 0, bufferSize)
          if (!Arrays.equals(buffer1, 0, nRead1, buffer2, 0, nRead2)) {
            return false
          }
          if (nRead1 < bufferSize) {
            // we've reached the end of the files, but found no mismatch
            return true
          }
          totalRead += nRead1
        }
      }
    }
  }

  private static Map<String, Path> collectFilesUnder(@NotNull Path rootDir) {
    Map<String, Path> result = new HashMap<>()
    Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
      @Override
      FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        result.put(FileUtilRt.toSystemIndependentName(rootDir.relativize(file).toString().replace(File.separatorChar, '/' as char)), file)
        return FileVisitResult.CONTINUE
      }
    })
    return result
  }
}
