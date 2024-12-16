// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.getEelApi
import com.sun.jdi.InternalException
import com.sun.jdi.ObjectCollectedException
import com.sun.jdi.VMDisconnectedException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createFile

internal inline fun <T, E : Exception> suppressExceptions(
  defaultValue: T?,
  rethrow: Class<E>? = null,
  supplier: () -> T?,
): T? = try {
  supplier()
}
catch (e: Throwable) {
  when (e) {
    is ProcessCanceledException, is CancellationException, is VMDisconnectedException, is ObjectCollectedException -> {
      throw e
    }
    is InternalException -> {
      fileLogger().info(e)
    }
    is Exception, is AssertionError -> {
      if (rethrow != null && rethrow.isInstance(e)) {
        throw e
      }
      fileLogger().error(e)
    }
    else -> throw e
  }
  defaultValue
}

/**
 * We are going to pass this file to a remote process, which may run in an isolated environment. In this case we need to create a file on the remote side.
 */
internal fun createLocalizedTempFile(project: Project?, prefix: String, suffix: String): Pair<Path, URI> {
  val projectFilePath = project?.projectFilePath
  if (!Registry.`is`("compiler.build.can.use.eel") || projectFilePath == null) {
    val localFile = Files.createTempFile(prefix, suffix)
    return localFile to localFile.toUri()
  }
  return runBlockingMaybeCancellable {
    val eelApi = Path.of(projectFilePath).getEelApi()
    val eelPath = eelApi.fs.createTemporaryDirectory(EelFileSystemApi.CreateTemporaryDirectoryOptions.Builder().build()).getOrThrow().resolve(prefix + suffix)
    eelApi.mapper.toNioPath(eelPath).apply { createFile() }
    eelApi.mapper.toNioPath(eelPath) to URI("file:$eelPath")
  }
}

// do not catch VMDisconnectedException
inline fun <T : Any, R> computeSafeIfAny(ep: ExtensionPointName<T>, processor: (T) -> R?): R? =
  ep.extensionList.firstNotNullOfOrNull { t ->
    try {
      processor(t)
    }
    catch (e: Exception) {
      if (e is ProcessCanceledException || e is VMDisconnectedException || e is CancellationException) {
        throw e
      }
      fileLogger().error(e)
      null
    }
  }
