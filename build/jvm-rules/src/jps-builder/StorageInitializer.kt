// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.RequestLog
import org.jetbrains.bazel.jvm.jps.impl.createJpsProjectDescriptor
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.model.JpsModel
import java.nio.file.Files
import java.nio.file.Path

internal class StorageInitializer(private val dataDir: Path) {
  private var wasCleared = false

  fun clearAndInit(@Suppress("unused") span: Span) {
    clearStorage()
    Files.createDirectories(dataDir)
  }

  fun createProjectDescriptor(
    jpsModel: JpsModel,
    moduleTarget: BazelModuleBuildTarget,
    relativizer: PathRelativizerService,
    buildDataProvider: BazelBuildDataProvider,
    requestLog: RequestLog,
    span: Span,
  ): ProjectDescriptor {
    try {
      return createJpsProjectDescriptor(
        dataStorageRoot = dataDir,
        // alwaysScanFS doesn't matter, we use our own version of `BuildOperations.ensureFSStateInitialized`,
        // see `JpsProjectBuilder.ensureFsStateInitialized`
        fsState = BuildFSState(/* alwaysScanFS = */ true),
        jpsModel = jpsModel,
        moduleTarget = moduleTarget,
        relativizer = relativizer,
        buildDataProvider = buildDataProvider,
        requestLog = requestLog,
      )
    }
    catch (e: Throwable) {
      //storageManager!!.forceClose()
      if (wasCleared) {
        throw e
      }

      span.recordException(e, Attributes.of(AttributeKey.stringKey("message"), "cannot open cache storage"))
    }

    clearStorage()

    return createProjectDescriptor(
      jpsModel = jpsModel,
      moduleTarget = moduleTarget,
      relativizer = relativizer,
      buildDataProvider = buildDataProvider,
      requestLog = requestLog,
      span = span,
    )
  }

  fun clearStorage() {
    wasCleared = true
    // todo rename and store
    deleteDirs()
  }

  private fun deleteDirs() {
    FileUtilRt.deleteRecursively(dataDir)
  }
}