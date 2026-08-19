// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

/**
 * List of custom loggers for IJent.
 *
 * It's relatively safe to enable all loggers in the debug level.
 * However, all these loggers in trace/all level together can produce 50 MiB text logs per second.
 * Enable trace loggers cautiously.
 *
 * When it comes to choosing a logger for infos and warnings, it's fine to choose [OTHER_LOG],
 * because all loggers write info, warn and error messages to the `idea.log` file anyway.
 *
 * Please don't put avoidable logic into this file. Being a small file with the declarative structure,
 * this file can also work as the logging glossary for people not familiar with internals of the platform.
 *
 * Q: Why not use the usual `Logger.getInstance(className)` and analogs instead of a limited pre-defined set of loggers?
 * A: The pattern of getting a logger for a class allows enabling debug logs for a specific class or a Java package,
 *    but not for the functionality. We often ask users to provide debug logs regarding IJent initialization,
 *    or regarding process execution, networking, etc. Every such a group is spread across many classes in many packages
 *    and many modules. The structure of Java packages does not represent the actual functionality.
 */
object IjentLogger {
  private val _all_loggers = linkedMapOf<String, IjentLog>()
  val ALL_LOGGERS: Map<String, IjentLog> get() = _all_loggers

  private fun logger(name: String): IjentLog {
    val l = IjentLog(createBackend(name))
    _all_loggers[name] = l
    return l
  }

  /**
   * Everything that happens during deploying IJent, launching its process, awaiting it, cleaning up,
   * so various things that are necessary for IJent but happen outside it.
   */
  // ...but such logging events may be added into ijent if anyone needs them there.
  val LIFETIME_LOG: IjentLog = logger("#com.intellij.platform.ijent.lifetime")

  val CONN_MGR_LOG: IjentLog = logger("#com.intellij.platform.ijent.conn_mgr")

  /**
   * Fetching environment variables.
   */
  val ENV_VAR_LOG: IjentLog = logger("#com.intellij.platform.ijent.env_var")

  /**
   * Processes: launch, stdio, exit codes, signals.
   */
  val EXEC_LOG: IjentLog = logger("#com.intellij.platform.ijent.exec")

  /**
   * External CLI handlers. Logs similar to processes.
   */
  val EXT_CLI_LOG: IjentLog = logger("#com.intellij.platform.ijent.ext_cli")

  /**
   * Requests to watch/unwatch files. List of changed files.
   */
  val FILE_WATCHER_LOG: IjentLog = logger("#com.intellij.platform.ijent.file_watcher")

  /**
   * All filesystem operations excluding reading and writing files.
   */
  val FS_LOG: IjentLog = logger("#com.intellij.platform.ijent.fs")

  /**
   * Dedicated logger for reading and writing files that can show contents of files.
   */
  val FS_FILE_CONTENT_LOG: IjentLog = logger("#com.intellij.platform.ijent.fs_file_content")

  /**
   * gRPC requests and responses.
   */
  val GRPC_LOG: IjentLog = logger("#com.intellij.platform.ijent.grpc")

  /**
   * Everything that doesn't fit the other categories.
   */
  val OTHER_LOG: IjentLog = logger("#com.intellij.platform.ijent.other")

  /**
   * TCP tunnels (TODO).
   * Unix sockets.
   */
  val TUNNELS_LOG: IjentLog = logger("#com.intellij.platform.ijent.tunnels")
}
