// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.gpg

import com.intellij.credentialStore.LOG
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.SmartList
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

internal class Pgp(private val gpgTool: GpgToolWrapper = createGpg()) {
  fun listKeys(): List<PgpKey> {
    val result = SmartList<PgpKey>()
    var keyId: String? = null
    for (line in StringUtilRt.convertLineSeparators(gpgTool.listSecretKeys()).splitToSequence('\n')) {
      val fields = line.splitToSequence(':').iterator()
      if (!fields.hasNext()) {
        continue
      }

      val tag = fields.next()
      when (tag) {
        "sec" -> {
          for (i in 2 until 5) {
            fields.next()
          }
          // Field 5 - KeyID
          keyId = fields.next()
        }

        "uid" -> {
          for (i in 2 until 10) {
            fields.next()
          }
          // Field 10 - User-ID
          // The value is quoted like a C string to avoid control characters (the colon is quoted =\x3a=).
          result.add(PgpKey(keyId!!, fields.next().replace("=\\x3a=", ":")))
          keyId = null
        }
      }
    }
    return result
  }

  fun decrypt(data: ByteArray) = gpgTool.decrypt(data)

  fun encrypt(data: ByteArray, recipient: String) = gpgTool.encrypt(data, recipient)
}

interface GpgToolWrapper {
  fun listSecretKeys(): String

  fun encrypt(data: ByteArray, recipient: String): ByteArray

  fun decrypt(data: ByteArray): ByteArray
}

internal fun createGpg(): GpgToolWrapper {
  val result = GpgToolWrapperImpl()
  try {
    result.version()
  }
  catch (e: Exception) {
    LOG.debug(e)
    return object : GpgToolWrapper {
      override fun encrypt(data: ByteArray, recipient: String) = throw UnsupportedOperationException()

      override fun decrypt(data: ByteArray) = throw UnsupportedOperationException()

      override fun listSecretKeys() = ""
    }
  }
  return result
}

internal class GpgToolWrapperImpl(private val gpgPath: String = "gpg", private val timeoutInMilliseconds: Int = 5000) : GpgToolWrapper {
  fun version(): String {
    val commandLine = createCommandLine()
    commandLine.addParameter("--version")
    return doExecute(commandLine)
  }

  // http://git.gnupg.org/cgi-bin/gitweb.cgi?p=gnupg.git;a=blob_plain;f=doc/DETAILS
  // https://delim.co/#
  override fun listSecretKeys(): String {
    val commandLine = createCommandLine()
    commandLine.addParameter("--list-secret-keys")
    return doExecute(commandLine)
  }

  override fun encrypt(data: ByteArray, recipient: String): ByteArray {
    val commandLine = createCommandLine()
    commandLine.addParameter("--encrypt")
    // key id is stored, --hidden-recipient doesn't make sense because if it will be used, key id should be specified somewhere else,
    // if it will be stored in master key metadata - for what to hide it then?
    commandLine.addParameter("--recipient")
    commandLine.addParameter(recipient)
    return doEncryptOrDecrypt(commandLine, data)
  }

  override fun decrypt(data: ByteArray): ByteArray {
    val commandLine = createCommandLine()
    commandLine.addParameter("--decrypt")
    return doEncryptOrDecrypt(commandLine, data)
  }

  private fun doEncryptOrDecrypt(commandLine: GeneralCommandLine, data: ByteArray): ByteArray {
    val process = commandLine.createProcess()
    val result = ByteArrayOutputStream()
    val future = ProcessIOExecutorService.INSTANCE.submit(Runnable {
      process.outputStream.use {
        it.write(data)
      }
      process.inputStream.use {
        FileUtilRt.copy(process.inputStream, result)
      }
    })

    // user need time to input passphrase/pin if need
    future.get(3, TimeUnit.MINUTES)
    process.waitFor(3, TimeUnit.MINUTES)
    val exitCode = process.exitValue()
    if (exitCode != 0) {
      throw RuntimeException("Cannot execute $gpgPath: exit code $exitCode, error output: ${result.toByteArray().toString(Charsets.UTF_8)}")
    }
    return result.toByteArray()
  }

  private fun doExecute(commandLine: GeneralCommandLine): String {
    val processOutput = ExecUtil.execAndGetOutput(commandLine, timeoutInMilliseconds)
    val exitCode = processOutput.exitCode
    if (exitCode != 0) {
      throw RuntimeException("Cannot execute $gpgPath: exit code $exitCode, error output: ${processOutput.stderr}")
    }
    return processOutput.stdout
  }

  private fun createCommandLine(): GeneralCommandLine {
    val commandLine = GeneralCommandLine()
    commandLine.exePath = gpgPath
    commandLine.addParameter("--with-colons")
    commandLine.addParameter("--no-tty")
    commandLine.addParameter("--yes")
    commandLine.addParameter("--quiet")
    commandLine.addParameter("--fixed-list-mode")
    commandLine.addParameter("--display-charset")
    commandLine.addParameter("utf-8")
    commandLine.addParameter("--no-greeting")
    return commandLine
  }
}

internal data class PgpKey(val keyId: String, val userId: String)