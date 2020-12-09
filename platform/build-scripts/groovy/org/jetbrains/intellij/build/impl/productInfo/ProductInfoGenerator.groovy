// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import com.google.gson.GsonBuilder
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily

import java.nio.file.Files
import java.nio.file.Path
/**
 * Generates product-info.json file containing meta-information about product installation.
 */
@CompileStatic
final class ProductInfoGenerator {
  public static final String FILE_NAME = "product-info.json"

  private final BuildContext context

  ProductInfoGenerator(BuildContext context) {
    this.context = context
  }

  void generateProductJson(@NotNull Path targetDirectory,
                           @NotNull String relativePathToBin,
                           @Nullable String startupWmClass,
                           @NotNull String launcherPath,
                           @Nullable String javaExecutablePath,
                           @NotNull String vmOptionsFilePath,
                           @NotNull OsFamily os) {
    generateMultiPlatformProductJson(targetDirectory, relativePathToBin, [
      new ProductInfoLaunchData(
        os: os.osName,
        startupWmClass: startupWmClass,
        launcherPath: launcherPath,
        javaExecutablePath: javaExecutablePath,
        vmOptionsFilePath: vmOptionsFilePath
    )])
  }

  void generateMultiPlatformProductJson(@NotNull Path targetDirectory, @NotNull String relativePathToBin, @NotNull List<ProductInfoLaunchData> launch) {
    def json = new ProductInfoData(
      name: context.applicationInfo.productName,
      version: context.applicationInfo.fullVersion,
      versionSuffix: context.applicationInfo.versionSuffix,
      buildNumber: context.buildNumber,
      productCode: context.applicationInfo.productCode,
      dataDirectoryName: context.systemSelector,
      svgIconPath: context.applicationInfo.svgRelativePath != null ? "$relativePathToBin/${context.productProperties.baseFileName}.svg" : null,
      launch: launch,
      customProperties: context.productProperties.generateCustomPropertiesForProductInfo()
    )
    Path file = targetDirectory.resolve(FILE_NAME)
    Files.createDirectories(targetDirectory)
    Files.newBufferedWriter(file).withCloseable {
      new GsonBuilder().setPrettyPrinting().create().toJson(json, it)
    }
  }
}