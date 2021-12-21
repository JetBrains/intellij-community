// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.fus.FeatureUsageStatisticsProperties

/**
 * Describes proprietary tools which are used to build the product. Pass the instance of this class to {@link BuildContext#createContext} method.
 */
@CompileStatic
@Canonical
final class ProprietaryBuildTools {
  public static final ProprietaryBuildTools DUMMY = new ProprietaryBuildTools(null, null, null, null, null, null)

  /**
   * This tool is required to sign *.exe files in Windows distribution. If it is {@code null} the files won't be signed and Windows may show
   * a warning when user tries to run them.
   */
  SignTool signTool

  /**
   * This tool is used to scramble the main product JAR file if {@link ProductProperties#scrambleMainJar} is {@code true}
   */
  ScrambleTool scrambleTool

  /**
   * Describes address and credentials of Mac machine which is used to sign and build *.dmg installer for macOS. If {@code null} only *.sit
   * archive will be built.
   */
  MacHostProperties macHostProperties

  /**
   * Describes a server that can be used to download built artifacts to install plugins into IDE
   */
  ArtifactsServer artifactsServer

  /**
   * Properties required to bundle a default version of feature usage statistics white list into IDE
   */
  FeatureUsageStatisticsProperties featureUsageStatisticsProperties

  /**
   * Generation of shared indexes and other tasks may require a valid license to run,
   * specify the license server URL to avoid hard-coding any license.
   */
  String licenseServerHost
}