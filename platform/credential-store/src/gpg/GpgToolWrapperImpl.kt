// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.gpg

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal class GpgToolWrapperImpl(private val gpgPath: String = "gpg", private val timeoutInMilliseconds: Int = 5000) : GpgToolWrapper {
  fun version(): String {
    val commandLine = createCommandLine()
    commandLine.addParameter("--version")
    return doExecute(commandLine)
  }

  // https://git.gnupg.org/cgi-bin/gitweb.cgi?p=gnupg.git;a=blob_plain;f=doc/DETAILS
  // https://delim.co/#
  override fun listSecretKeys(): String {
    val commandLine = createCommandLine()
    commandLine.addParameter("--list-secret-keys")
    return doExecute(commandLine)
  }

  override fun encrypt(data: ByteArray, recipient: String): ByteArray {
    val commandLine = createCommandLineForEncodeOrDecode()
    commandLine.addParameter("--encrypt")
    // key id is stored, --hidden-recipient doesn't make sense because if it is used, key id should be specified somewhere else,
    // if it will be stored in main key metadata - for what to hide it then?
    commandLine.addParameter("--recipient")
    commandLine.addParameter(recipient)
    return doEncryptOrDecrypt(commandLine, data)
  }

  override fun decrypt(data: ByteArray): ByteArray {
    val commandLine = createCommandLineForEncodeOrDecode()
    commandLine.addParameter("--decrypt")
    return doEncryptOrDecrypt(commandLine, data)
  }

  private fun createCommandLineForEncodeOrDecode(): GeneralCommandLine {
    // avoid error during encode/decode: `There is no assurance this key belongs to the named user` We can handle it somehow, but better to avoid at all.
    // key specified by key id and user is not able to input own custom value — instead, user forced to select from list populated from `gpg --list-secret-keys`
    // (so, definitely key is trusted as there is security key on local machine)
    // in our usage model, we can make user life a little bit easier and avoid complication on our side
    val result = createCommandLine(isAddStringOutputRelatedOptions = false)
    result.addParameter("--trust-model")
    result.addParameter("always")
    return result
  }

  private fun doEncryptOrDecrypt(commandLine: GeneralCommandLine, data: ByteArray): ByteArray {
    val process = commandLine.createProcess()
    val output = BufferExposingByteArrayOutputStream()
    val errorOutput = BufferExposingByteArrayOutputStream()
    try {
      CompletableFuture.allOf(
        runAsync {
          process.outputStream.use {
            it.write(data)
          }
        },
        runAsync {
          process.inputStream.use {
            FileUtilRt.copy(it, output)
          }
        },
        runAsync {
          process.errorStream.use {
            FileUtilRt.copy(it, errorOutput)
          }
        }
      ).get(3, TimeUnit.MINUTES /* user needs time to input passphrase/pin if needed */)
    }
    catch (e: ExecutionException) {
      throw RuntimeException("Cannot execute ${commandLine.commandLineString}\nerror output: ${errorOutput.toByteArray().toString(Charsets.UTF_8)}", e.cause)
    }

    // not clear in which condition process will not exited as soon as read futures completed, let's wait only 5 seconds
    if (!process.waitFor(5, TimeUnit.SECONDS)) {
      throw RuntimeException("Cannot execute $gpgPath: timeout")
    }

    val exitCode = process.exitValue()
    if (exitCode != 0) {
      throw RuntimeException("Cannot execute ${commandLine.commandLineString}\nexit code $exitCode, error output: ${errorOutput.toByteArray().toString(Charsets.UTF_8)}")
    }

    val result = output.toByteArray()
    val internalBuffer = output.internalBuffer
    if (result !== internalBuffer) {
      // ensure that if buffer was copied, the original internal buffer is cleared to avoid exposing sensitive data in memory
      internalBuffer.fill(0)
    }
    return result
  }

  private fun doExecute(commandLine: GeneralCommandLine): String {
    val processOutput = ExecUtil.execAndGetOutput(commandLine, timeoutInMilliseconds)
    val exitCode = processOutput.exitCode
    if (exitCode != 0) {
      throw RuntimeException("Cannot execute $gpgPath: exit code $exitCode, error output: ${processOutput.stderr}")
    }
    return processOutput.stdout
  }

  private fun createCommandLine(isAddStringOutputRelatedOptions: Boolean = true): GeneralCommandLine {
    val commandLine = GeneralCommandLine()
    commandLine.exePath = gpgPath
    if (isAddStringOutputRelatedOptions) {
      commandLine.addParameter("--with-colons")
      commandLine.addParameter("--fixed-list-mode")
    }
    commandLine.addParameter("--no-tty")
    commandLine.addParameter("--yes")

    commandLine.addParameter("--display-charset")
    commandLine.addParameter("utf-8")

    return commandLine
  }
}

private inline fun runAsync(crossinline task: () -> Unit): CompletableFuture<Void> {
  return CompletableFuture.runAsync(Runnable { task() }, AppExecutorUtil.getAppExecutorService())
}