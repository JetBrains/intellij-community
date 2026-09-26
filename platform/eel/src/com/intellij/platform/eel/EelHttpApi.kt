// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * HTTP downloads that run inside the environment, reached via [EelApi.http].
 *
 * The environment resolves the host, opens the connection, and writes the file. The payload never passes through the IDE host.
 * So a download inside a container or a WSL distribution uses the network and the proxy settings of that environment.
 *
 * ```kotlin
 * eel.http.download(target = EelPath.parse("/tmp/ijent.tar.gz", eel.descriptor), url = "https://example.com/ijent.tar.gz").eelIt()
 * ```
 */
@ApiStatus.Internal
interface EelHttpApi {
  val descriptor: EelDescriptor

  /**
   * Downloads [DownloadOptions.url] into the file [DownloadOptions.target] inside the environment.
   *
   * Redirects are followed. An HTTP status of 400 or above is a failure.
   * The parent directory of the target must exist. An existing target is overwritten.
   * A failed download can leave a partial file at the target.
   *
   * @throws IOException when the environment has no HTTP client, the host is unreachable, the server answers with an error status,
   *  or the target cannot be written.
   */
  @Throws(IOException::class)
  suspend fun download(@GeneratedBuilder options: DownloadOptions)

  @ApiStatus.Internal
  interface DownloadOptions {
    /** An absolute `http` or `https` URL. */
    val url: String

    /** The file to write inside the environment. */
    val target: EelPath
  }
}
