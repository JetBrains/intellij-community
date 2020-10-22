// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.intellij.execution.process.mediator.daemon

import com.intellij.execution.process.mediator.util.parseArgs
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.system.exitProcess

data class DaemonLaunchOptions(
  val helloOption: HelloOption? = null,
  val tokenEncryptionOption: TokenEncryptionOption? = null,
) {
  interface CmdlineOption {
    override fun toString(): String
  }

  sealed class HelloOption(private val arg: String) : CmdlineOption {
    override fun toString(): String = arg

    object Stdout : HelloOption("--hello-file=-")

    class File(val path: Path) : HelloOption("--hello-file=$path") {
      constructor(s: String) : this(Path.of(s))
    }

    class Port(val port: UShort) : HelloOption("--hello-port=$port") {
      constructor(s: String) : this(s.toUShort())
    }
  }

  class TokenEncryptionOption(val publicKey: PublicKey) {
    constructor(s: String) : this(rsaPublicKeyFromBytes(Base64.getDecoder().decode(s)))

    override fun toString(): String = "--token-encrypt-rsa=${Base64.getEncoder().encodeToString(publicKey.encoded)}"

    companion object {
      private fun rsaPublicKeyFromBytes(bytes: ByteArray) =
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    }
  }

  fun asCmdlineArgs(): List<String> {
    return listOf(
      helloOption,
      tokenEncryptionOption,
    ).mapNotNull { it?.toString() }
  }

  override fun toString(): String {
    return asCmdlineArgs().joinToString(" ")
  }

  companion object {
    private fun printUsage(programName: String) {
      System.err.println("Usage: $programName [ --hello-file=file|- | --hello-port=port ] [ --token-encrypt-rsa=public-key ]")
    }

    fun parseFromArgsOrDie(programName: String, args: Array<String>): DaemonLaunchOptions {
      return try {
        parseFromArgs(args)
      }
      catch (e: Exception) {
        System.err.println(e.message)
        printUsage(programName)
        exitProcess(1)
      }
    }

    private fun parseFromArgs(args: Array<String>): DaemonLaunchOptions {
      var helloOption: HelloOption? = null
      var tokenEncryptionOption: TokenEncryptionOption? = null

      for ((option, value) in parseArgs(args)) {
        requireNotNull(value) { "Missing '$option' value" }

        when (option) {
          "--hello-file" -> {
            if (helloOption == null) {
              helloOption =
                if (value == "-") HelloOption.Stdout
                else HelloOption.File(value)
            }
            else System.err.println("Ignoring '$option'")
          }

          "--hello-port" -> {
            // handling multiple hello writers is too complicated w.r.t. resource management
            if (helloOption == null) {
              helloOption = HelloOption.Port(value)
            }
            else System.err.println("Ignoring '$option'")
          }

          "--token-encrypt-rsa" -> {
            tokenEncryptionOption = TokenEncryptionOption(value)
          }

          null -> throw IllegalArgumentException("Unrecognized positional argument '$value'")
          else -> throw IllegalArgumentException("Unrecognized option '$option'")
        }
      }

      return DaemonLaunchOptions(
        helloOption = helloOption,
        tokenEncryptionOption = tokenEncryptionOption,
      )
    }
  }
}
