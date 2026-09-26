// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.buildtool.cli.worker

import fleet.buildtool.cli.main as runCli
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.WorkRequestReaderWithoutDigest
import org.jetbrains.bazel.jvm.processRequests

fun main(args: Array<String>) {
  processRequests(
    startupArgs = args,
    executorFactory = { _, _ ->
      WorkRequestExecutor { request, _, _, _ ->
        runCli(request.arguments)
        0
      }
    },
    reader = WorkRequestReaderWithoutDigest(System.`in`),
    serviceName = "community-fleet-cli-worker",
  )
}
