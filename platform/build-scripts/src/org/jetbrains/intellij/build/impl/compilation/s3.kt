// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private val isAwsCliAvailable by lazy {
  ProcessBuilder("aws", "--version").inheritIO().start().waitWithTimeout() == 0
}

private fun Process.waitWithTimeout(): Int {
  if (!waitFor(5, TimeUnit.MINUTES)) {
    require(destroyForcibly().waitFor() == 0) {
      "Unable to stop process ${pid()}"
    }
  }
  return exitValue()
}

internal fun String.isS3() = startsWith("s3://")

/**
 * Executes 'aws s3 [args]' process
 */
internal fun awsS3Cli(vararg args: String, returnStdOut: Boolean = true): String {
  require(args.isNotEmpty())
  check(isAwsCliAvailable) {
    "AWS CLI is required"
  }
  requireNotNull(System.getenv("AWS_ACCESS_KEY_ID")) {
    "AWS_ACCESS_KEY_ID environment variable is required"
  }
  requireNotNull(System.getenv("AWS_SECRET_ACCESS_KEY")) {
    "AWS_SECRET_ACCESS_KEY environment variable is required"
  }
  val process = with(ProcessBuilder("aws", "s3", *args)) {
    if (!returnStdOut) inheritIO()
    start()
  }
  val output = if (returnStdOut)
    process.inputStream.use {
      String(it.readAllBytes(), StandardCharsets.UTF_8)
    }
  else {
    ""
  }
  val exitCode = process.waitWithTimeout()
  require(exitCode == 0) {
    "'aws s3 ${args.joinToString(separator = " ")}' exited with $exitCode"
  }
  return output
}