// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import com.google.gson.GsonBuilder
import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily

/**
 * Generates product-info.json file containing meta-information about product installation.
 */
@CompileStatic
class ProductInfoGenerator {
  public static final String FILE_NAME = "product-info.json"

  private final BuildContext context

  ProductInfoGenerator(BuildContext context) {
    this.context = context
  }

  void generateProductJson(@NotNull String targetDirectory,
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

  void generateMultiPlatformProductJson(@NotNull String targetDirectory, @NotNull String relativePathToBin, @NotNull List<ProductInfoLaunchData> launch) {
    def json = new ProductInfoData(
      name: context.applicationInfo.productName,
      version: context.applicationInfo.fullVersion,
      versionSuffix: context.applicationInfo.versionSuffix,
      buildNumber: context.buildNumber,
      productCode: context.applicationInfo.productCode,
      svgIconPath: context.applicationInfo.svgRelativePath != null ? "$relativePathToBin/${context.productProperties.baseFileName}.svg" : null,
      launch: launch
    )
    def file = new File(targetDirectory, FILE_NAME)
    FileUtil.createParentDirs(file)

    file.withWriter {
      new GsonBuilder().setPrettyPrinting().create().toJson(json, it)
    }
  }
}