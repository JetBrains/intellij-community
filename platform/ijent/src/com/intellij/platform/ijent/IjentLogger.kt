// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.diagnostic.Logger

/**
 * List of custom loggers for IJent.
 *
 * It's relatively safe to enable all loggers in the debug level.
 * However, all these loggers in trace/all level together can produce 50 MiB text logs per second.
 * Enable trace loggers cautiously.
 */
object IjentLogger {
  /**
   * Fetching environment variables.
   */
  val ENV_VAR_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.env_var")

  /**
   * Processes: launch, stdio, exit codes, signals.
   */
  val EXEC_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.exec")

  /**
   * External CLI handlers. Logs similar to processes.
   */
  val EXT_CLI_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.ext_cli")

  /**
   * Requests to watch/unwatch files. List of changed files.
   */
  val FILE_WATCHER_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.file_watcher")

  /**
   * All filesystem operations excluding reading and writing files.
   */
  val FS_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.fs")

  /**
   * Dedicated logger for reading and writing files that can show contents of files.
   */
  val FS_FILE_CONTENT_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.fs_file_content")

  /**
   * gRPC requests and responses.
   */
  val GRPC_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.grpc")

  /**
   * Traffic dump for virtual machine sockets when IJent runs inside Hyper-V VM.
   */
  val HYPERV_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.hyperv")

  /**
   * Everything that doesn't fit the other categories.
   */
  val OTHER_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.other")

  /**
   * TCP tunnels (TODO).
   * Unix sockets.
   */
  val TUNNELS_LOG: Logger = Logger.getInstance("#com.intellij.platform.ijent.tunnels")

  val ALL_LOGGERS: Collection<Logger> by lazy {
    IjentLogger::class.java.declaredFields
      .filter { it.type == Logger::class.java }
      .map { field -> field.get(this) as Logger }
  }
}