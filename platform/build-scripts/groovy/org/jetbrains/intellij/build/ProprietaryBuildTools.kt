// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.fus.FeatureUsageStatisticsProperties

/**
 * Describes proprietary tools which are used to build the product. Pass the instance of this class to {@link BuildContext#createContext} method.
 */
class ProprietaryBuildTools(
  /**
   * This tool is required to sign *.exe files in Windows distribution. If it is {@code null} the files won't be signed and Windows may show
   * a warning when user tries to run them.
   */
  val signTool: SignTool?,

  /**
   * This tool is used to scramble the main product JAR file if {@link ProductProperties#scrambleMainJar} is {@code true}
   */
  val scrambleTool: ScrambleTool?,

  /**
   * Describes address and credentials of Mac machine which is used to sign and build *.dmg installer for macOS. If {@code null} only *.sit
   * archive will be built.
   */
   val macHostProperties: MacHostProperties?,

  /**
   * Describes a server that can be used to download built artifacts to install plugins into IDE
   */
  val artifactsServer: ArtifactsServer?,

  /**
   * Properties required to bundle a default version of feature usage statistics white list into IDE
   */
  val featureUsageStatisticsProperties: FeatureUsageStatisticsProperties?,

  /**
   * Generation of shared indexes and other tasks may require a valid license to run,
   * specify the license server URL to avoid hard-coding any license.
   */
  val licenseServerHost: String?
) {
  companion object {
    @JvmStatic
    val DUMMY = ProprietaryBuildTools(signTool = null,
                                      scrambleTool = null,
                                      macHostProperties = null,
                                      artifactsServer = null,
                                      featureUsageStatisticsProperties = null,
                                      licenseServerHost = null)
  }
}
