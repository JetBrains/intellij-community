// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import com.fasterxml.jackson.jr.ob.JSON
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.SkipTransientPropertiesJrExtension

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
    Path file = targetDirectory.resolve(FILE_NAME)
    Files.createDirectories(targetDirectory)
    Files.write(file, generateMultiPlatformProductJson(relativePathToBin, [
      new ProductInfoLaunchData(
        os: os.osName,
        startupWmClass: startupWmClass,
        launcherPath: launcherPath,
        javaExecutablePath: javaExecutablePath,
        vmOptionsFilePath: vmOptionsFilePath
      )])
    )
  }

  byte[] generateProductJson(@NotNull String relativePathToBin,
                             @Nullable String startupWmClass,
                             @NotNull String launcherPath,
                             @Nullable String javaExecutablePath,
                             @NotNull String vmOptionsFilePath,
                             @NotNull OsFamily os) {
    return generateMultiPlatformProductJson(relativePathToBin, [
      new ProductInfoLaunchData(
        os: os.osName,
        startupWmClass: startupWmClass,
        launcherPath: launcherPath,
        javaExecutablePath: javaExecutablePath,
        vmOptionsFilePath: vmOptionsFilePath
    )])
  }

  byte[] generateMultiPlatformProductJson(@NotNull String relativePathToBin, @NotNull List<ProductInfoLaunchData> launch) {
    ApplicationInfoProperties appInfo = context.applicationInfo
    ProductInfoData json = new ProductInfoData(
      name: appInfo.productName,
      version: appInfo.fullVersion,
      versionSuffix: appInfo.versionSuffix,
      buildNumber: context.buildNumber,
      productCode: appInfo.productCode,
      dataDirectoryName: context.systemSelector,
      svgIconPath: appInfo.svgRelativePath == null ? null : "$relativePathToBin/${context.productProperties.baseFileName}.svg",
      launch: launch,
      customProperties: context.productProperties.generateCustomPropertiesForProductInfo()
    )
    return JSON.builder().enable(JSON.Feature.PRETTY_PRINT_OUTPUT).register(new SkipTransientPropertiesJrExtension()).build().asBytes(json)
  }
}
