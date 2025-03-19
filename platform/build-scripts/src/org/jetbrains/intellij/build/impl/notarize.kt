// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.jetbrains.notary.NotaryClientV2
import com.jetbrains.notary.auth.AppStoreConnectAPIKey
import com.jetbrains.notary.extensions.StatusPollingConfiguration
import com.jetbrains.notary.extensions.notarize
import com.jetbrains.notary.models.SubmissionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions.Companion.MAC_NOTARIZE_STEP
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.executeStep
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val issuerId = System.getenv("APPLE_ISSUER_ID") ?: ""
private val keyId = System.getenv("APPLE_KEY_ID") ?: ""
private val privateKey = System.getenv("APPLE_PRIVATE_KEY") ?: ""
private val json = Json { prettyPrint = true }

private fun useNotaryRestApi() = keyId.isNotBlank() && privateKey.isNotBlank() && issuerId.isNotBlank()

internal suspend fun notarize(sitFile: Path, context: BuildContext) {
  context.executeStep(spanBuilder("Notarizing .sit via Notary REST API").setAttribute("sitFile", "$sitFile"), MAC_NOTARIZE_STEP) {
    require(useNotaryRestApi()) {
      "Blank/missing environment variables APPLE_KEY_ID or APPLE_PRIVATE_KEY or APPLE_ISSUER_ID"
    }
    val credentials = AppStoreConnectAPIKey(
      issuerId = issuerId,
      keyId = keyId,
      privateKey = privateKey,
    )
    val notaryApiClient = NotaryClientV2(credentials)
    val statusPollingConfiguration = StatusPollingConfiguration(
      timeout = 5.hours,
      pollingPeriod = 1.minutes,
      ignoreServerError = true,
      ignoreTimeoutExceptions = true,
    )
    val result = withContext(Dispatchers.IO) {
      // only .zip or .dmg files can be notarized
      val zipFile = Files.move(sitFile, sitFile.resolveSibling(sitFile.nameWithoutExtension + ".zip"), StandardCopyOption.REPLACE_EXISTING)
      val result = notaryApiClient.notarize(zipFile, statusPollingConfiguration)
      Files.move(zipFile, sitFile)
      result
    }
    val logs = json.encodeToString(result.logs)
    context.messages.info("Notarization logs:\n$logs")
    val logFile = context.paths.artifactDir
      .resolve("macos-logs")
      .resolve("notarize-${sitFile.name}.log")
    Files.createDirectories(logFile.parent)
    logFile.writeText(logs)
    when (result.status) {
      SubmissionResponse.Status.ACCEPTED -> {
        context.messages.info("Notarization of $sitFile successful")
      }
      SubmissionResponse.Status.IN_PROGRESS, null -> {
        context.messages.error("Notarization of $sitFile timed out")
      }
      SubmissionResponse.Status.INVALID, SubmissionResponse.Status.REJECTED -> {
        context.messages.error("Notarization of $sitFile failed, see logs above")
      }
    }
    context.notifyArtifactBuilt(logFile)
  }
}