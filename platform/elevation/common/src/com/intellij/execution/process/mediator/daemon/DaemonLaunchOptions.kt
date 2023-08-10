// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.intellij.execution.process.mediator.util.parseArgs
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.system.exitProcess

data class DaemonLaunchOptions(
  val trampoline: Boolean = false,
  val daemonize: Boolean = false,
  val leaderPid: Long? = null,
  val machNamespaceUid: Int? = null,
  val handshakeOption: HandshakeOption? = null,
  val tokenEncryptionOption: TokenEncryptionOption? = null,
) {
  interface CmdlineOption {
    override fun toString(): String
  }

  // handling multiple handshake writers is too complicated w.r.t. resource management, and we never need it anyway
  sealed class HandshakeOption(private val arg: String) : CmdlineOption {
    override fun toString(): String = arg

    object Stdout : HandshakeOption("--handshake-file=-")

    class File(val path: Path) : HandshakeOption("--handshake-file=$path") {
      constructor(s: String) : this(Path.of(s))
    }

    class Port(val port: Int) : HandshakeOption("--handshake-port=$port") {
      constructor(s: String) : this(s.toInt())
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
      "--trampoline".takeIf { trampoline },
      "--daemonize".takeIf { daemonize },
      leaderPid?.let { "--leader-pid=$it" },
      machNamespaceUid?.let { "--mach-namespace-uid=$it" },
      handshakeOption,
      tokenEncryptionOption,
    ).mapNotNull { it?.toString() }
  }

  override fun toString(): String {
    return asCmdlineArgs().joinToString(" ")
  }

  companion object {
    private fun printUsage(programName: String) {
      System.err.println(
        "Usage: $programName" +
        " [ --trampoline ]" +
        " [ --daemonize ]" +
        " [ --leader-pid=pid ]" +
        " [ --mach-namespace-uid=uid ]" +
        " [ --handshake-file=file|- | --handshake-port=port ]" +
        " [ --token-encrypt-rsa=public-key ]"
      )
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
      var trampoline = false
      var daemonize = false
      var leaderPid: Long? = null
      var machNamespaceUid: Int? = null
      var handshakeOption: HandshakeOption? = null
      var tokenEncryptionOption: TokenEncryptionOption? = null

      for ((option, value) in parseArgs(args)) {
        when (option) {
          "--trampoline" -> trampoline = true
          "--no-trampoline" -> trampoline = false
          "--daemonize" -> daemonize = true
          "--no-daemonize" -> daemonize = false
          else -> requireNotNull(value) { "Missing '$option' value" }
        }
        value ?: continue

        when (option) {
          "--leader-pid" -> {
            leaderPid = value.toLong()
          }

          "--mach-namespace-uid" -> {
            machNamespaceUid = value.toInt()
          }

          "--handshake-file" -> {
            handshakeOption =
              if (value == "-") HandshakeOption.Stdout
              else HandshakeOption.File(value)
          }

          "--handshake-port" -> {
            handshakeOption = HandshakeOption.Port(value)
          }

          "--token-encrypt-rsa" -> {
            tokenEncryptionOption = TokenEncryptionOption(value)
          }

          null -> throw IllegalArgumentException("Unrecognized positional argument '$value'")
          else -> throw IllegalArgumentException("Unrecognized option '$option'")
        }
      }

      return DaemonLaunchOptions(
        trampoline = trampoline,
        daemonize = daemonize,
        leaderPid = leaderPid,
        machNamespaceUid = machNamespaceUid,
        handshakeOption = handshakeOption,
        tokenEncryptionOption = tokenEncryptionOption,
      )
    }
  }
}
