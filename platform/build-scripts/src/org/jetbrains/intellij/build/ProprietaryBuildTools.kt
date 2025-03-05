// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.intellij.build.fus.FeatureUsageStatisticsProperties
import java.nio.file.Path

/**
 * Describes proprietary tools which are used to build the product. Pass the instance of this class to [org.jetbrains.intellij.build.impl.BuildContextImpl.Companion.createContext] method.
 */
data class ProprietaryBuildTools(
  /**
   * This tool is required to sign files in distribution. If it is null, the files won't be signed and OS may show a warning when a user tries to run them.
   */
  val signTool: SignTool,

  /**
   * This tool is used to scramble the main product JAR file if [ProductProperties.scrambleMainJar] is `true`
   */
  val scrambleTool: ScrambleTool?,

  /**
   * Describes address and credentials of Mac machine which is used to sign and build *.dmg installer for macOS. If `null` only *.sit
   * archive will be built.
   */
  val macOsCodesignIdentity: MacOsCodesignIdentity?,

  /**
   * Describes a server that can be used to download built artifacts to install plugins into IDE
   */
  val artifactsServer: ArtifactsServer?,

  /**
   * Properties required to bundle a default version of feature usage statistics allowlist into the IDE
   */
  val featureUsageStatisticsProperties: List<FeatureUsageStatisticsProperties>?,

  /**
   * Generation of shared indexes and other tasks may require a valid license to run,
   * specify the license server URL to avoid hard-coding any license.
   */
  val licenseServerHost: String?
) {
  companion object {
    internal val DUMMY_SIGN_TOOL: SignTool = object : SignTool {
      override val signNativeFileMode: SignNativeFileMode
        get() = SignNativeFileMode.DISABLED

      override suspend fun signFiles(files: List<Path>, context: BuildContext?, options: PersistentMap<String, String>) {
        Span.current().addEvent(
          "files won't be signed", Attributes.of(
          AttributeKey.stringArrayKey("files"), files.map(Path::toString),
          AttributeKey.stringKey("reason"), "sign tool isn't defined",
        )
        )
      }

      override suspend fun signFilesWithGpg(files: List<Path>, context: BuildContext) {
        signFiles(files, context, persistentMapOf())
      }

      override suspend fun getPresignedLibraryFile(path: String, libName: String, libVersion: String, context: BuildContext): Path? {
        error("Must be not called if signNativeFileMode equals to $signNativeFileMode")
      }

      override suspend fun commandLineClient(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Path? {
        return null
      }
    }

    val DUMMY: ProprietaryBuildTools = ProprietaryBuildTools(
      signTool = DUMMY_SIGN_TOOL,
      scrambleTool = null,
      macOsCodesignIdentity = null,
      artifactsServer = null,
      featureUsageStatisticsProperties = null,
      licenseServerHost = null
    )
  }
}
