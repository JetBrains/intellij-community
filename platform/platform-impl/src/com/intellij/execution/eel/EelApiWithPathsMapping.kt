// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.execution.ijent.nio.IjentEphemeralRootAwarePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.unwrap
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * This class provides an implementation of the [EelApi] interface, extending the functionality of [EelApiBase]
 * by mapping paths based on an ephemeral root @see [com.intellij.execution.ijent.nio.IjentEphemeralRootAwareFileSystem].
 * It delegates most responsibilities to an existing [EelApiBase]
 * implementation, ensuring path normalization is also handled when necessary.
 *
 * @property ephemeralRoot The root path used for ephemeral storage.
 * @property original An [EelApiBase] instance to which operations are delegated.
 */
@Internal
class EelApiWithPathsMapping(private val ephemeralRoot: Path, private val original: EelApiBase) : EelApiBase by original, EelApi {
  private fun normalizeIfPath(maybePath: String): String {
    return if (maybePath.startsWith(ephemeralRoot.pathString)) {
      val originalPath = mapper.getOriginalPath(Path.of(maybePath))?.toString()

      originalPath ?: maybePath.removePrefix(ephemeralRoot.pathString).let {
        if (original is EelPosixApi) FileUtil.toSystemIndependentName(it) else it
      }
    }
    else {
      maybePath
    }
  }

  override val exec: EelExecApi
    get() = EelExecApiWithNormalization(original.exec) { EelProcessBuilderWithPathsNormalization(it, ::normalizeIfPath) }

  override val mapper: EelPathMapper get() = EelEphemeralRootAwareMapper(ephemeralRoot, original)
}

private class EelEphemeralRootAwareMapper(
  private val ephemeralRoot: Path,
  private val eelApi: EelApiBase,
) : EelPathMapper {
  override fun getOriginalPath(path: Path): EelPath.Absolute? {
    return if (path is IjentEphemeralRootAwarePath) {
      eelApi.fs.getPath(path.originalPath.toString()).unwrap()
    }
    else {
      null
    }
  }

  override fun toNioPath(path: EelPath.Absolute): Path {
    return ephemeralRoot.resolve(path.toString())
  }
}

private class EelProcessBuilderWithPathsNormalization(
  private val original: EelExecApi.ExecuteProcessBuilder,
  private val normalizeIfPath: (String) -> String,
) : EelExecApi.ExecuteProcessBuilder by original {
  override val workingDirectory: String? = null; get() = field?.let { normalizeIfPath(it) }
  override val args: List<String> = original.args.map { normalizeIfPath(it) }
  override val env: Map<String, String> = original.env.mapValues { (_, value) -> normalizeIfPath(value) }
}

private class EelExecApiWithNormalization(
  private val original: EelExecApi,
  private val normalize: (EelExecApi.ExecuteProcessBuilder) -> EelExecApi.ExecuteProcessBuilder,
) : EelExecApi by original {
  override suspend fun execute(builder: EelExecApi.ExecuteProcessBuilder): EelExecApi.ExecuteProcessResult {
    return original.execute(normalize(builder))
  }
}