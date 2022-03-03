// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

import java.nio.file.Path

@CompileStatic
final class CrossPlatformDistributionBuilder {
  static Path buildCrossPlatformZip(Map<OsFamily, Path> distDirs, BuildContext context) {
    String executableName = context.productProperties.baseFileName

    byte[] productJson = new ProductInfoGenerator(context).generateMultiPlatformProductJson("bin", List.of(
      new ProductInfoLaunchData(OsFamily.WINDOWS.osName, "bin/${executableName}.bat", null, "bin/win/${executableName}64.exe.vmoptions",
                                null),
      new ProductInfoLaunchData(OsFamily.LINUX.osName, "bin/${executableName}.sh", null, "bin/linux/${executableName}64.vmoptions",
                                LinuxDistributionBuilder.getFrameClass(context)),
      new ProductInfoLaunchData(OsFamily.MACOS.osName, "MacOS/$executableName", null, "bin/mac/${executableName}.vmoptions", null)
    ))

    String zipFileName = context.productProperties.getCrossPlatformZipFileName(context.applicationInfo, context.buildNumber)
    Path targetFile = context.paths.artifactDir.resolve(zipFileName)

    BuildHelper.getInstance(context).crossPlatformArchive.invokeWithArguments(
      distDirs.get(OsFamily.MACOS), distDirs.get(OsFamily.LINUX), distDirs.get(OsFamily.WINDOWS),
      targetFile,
      executableName,
      productJson,
      context.macDistributionCustomizer.extraExecutables,
      context.linuxDistributionCustomizer.extraExecutables,
      context.distFiles,
      Map.of("dependencies.txt", context.dependenciesProperties.file),
      context.paths.distAllDir,
      )

    ProductInfoValidator.checkInArchive(context, targetFile, "")
    context.notifyArtifactBuilt(targetFile)

    Map<String, String> checkerConfig = context.productProperties.versionCheckerConfig
    if (checkerConfig != null) {
      ClassVersionChecker.checkVersions(checkerConfig, context, targetFile)
    }
    return targetFile
  }
}
