// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.macOS

import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.io.runProcess
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

/**
 * Patches UUID value in the Mach-O [executable] with the [MacDistributionCustomizer.getDistributionUUID].
 * No changes to the [executable] will be made if no re-signing can be performed.
 * Only single-arch 64-bit files are supported.
 */
@ApiStatus.Internal
class MachOUuid(private val executable: Path, private val customizer: MacDistributionCustomizer, private val context: BuildContext) {
  /**
   * The 64-bit mach header appears at the very beginning of object files for 64-bit architectures:
   * ```
   * struct mach_header_64 {
   * 	uint32_t	magic;		/* mach magic number identifier */
   * 	cpu_type_t	cputype;	/* cpu specifier */
   * 	cpu_subtype_t	cpusubtype;	/* machine specifier */
   * 	uint32_t	filetype;	/* type of file */
   * 	uint32_t	ncmds;		/* number of load commands */
   * 	uint32_t	sizeofcmds;	/* the size of all the load commands */
   * 	uint32_t	flags;		/* flags */
   * 	uint32_t	reserved;	/* reserved */
   * };
   * ```
   *
   * The load commands directly follow the mach_header:
   * ```
   * struct load_command {
   *  uint32_t cmd; /* type of load command */
   *  uint32_t cmdsize;	/* total size of command in bytes */
   * };
   * ```
   *
   * See https://github.com/apple-oss-distributions/xnu/blob/main/EXTERNAL_HEADERS/mach-o/loader.h
   */
  private companion object {
    const val LC_UUID: Int = 0x1b

    /**
     * [Integer.toHexString] is "feedfacf"
     */
    const val MH_MAGIC_64: Int = -0x1120531

    /**
     * `mach_header_64.magic` size is four bytes (`uint32_t`)
     */
    const val MACH_HEADER_64_MAGIC_SIZE_IN_BYTES: Int = 4

    /**
     * The start position of `mach_header_64.ncmds`
     */
    const val MACH_HEADER_64_NCMDS_POSITION_IN_BYTES: Long = 16

    /**
     * `load_command.cmd`(`uint32_t`) + `load_command.cmdsize`(`uint32_t`)
     */
    const val LOAD_COMMAND_HEADER_SIZE_IN_BYTES: Int = 8

    /**
     * The start position of `load_command`s, right after the `mach_header_64`
     */
    const val LOAD_COMMANDS_POSITION_IN_BYTES: Long = 32
  }

  private val canBeSignedLocally: Boolean = context.options.isInDevelopmentMode && SystemInfoRt.isMac

  private fun checkMagic(channel: SeekableByteChannel, buffer: ByteBuffer) {
    channel.read(buffer)
    buffer.flip()
    val magic = buffer.getInt()
    check(magic == MH_MAGIC_64) { "Not a valid 64-bit Mach-O file: 0x" + Integer.toHexString(magic) }
    buffer.clear()
  }

  private fun readNumberOfLoadCommands(channel: SeekableByteChannel, buffer: ByteBuffer): Int {
    channel.position(MACH_HEADER_64_NCMDS_POSITION_IN_BYTES)
    channel.read(buffer)
    buffer.flip()
    return buffer.getInt()
  }

  suspend fun patch() {
    Files.newByteChannel(executable, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
      var buffer = ByteBuffer.allocate(MACH_HEADER_64_MAGIC_SIZE_IN_BYTES).order(ByteOrder.LITTLE_ENDIAN)
      checkMagic(channel, buffer)
      val nCmds = readNumberOfLoadCommands(channel, buffer)
      buffer = ByteBuffer.allocate(LOAD_COMMAND_HEADER_SIZE_IN_BYTES).order(ByteOrder.LITTLE_ENDIAN)
      channel.position(LOAD_COMMANDS_POSITION_IN_BYTES)
      (0..<nCmds).forEach {
        buffer.clear()
        channel.read(buffer)
        buffer.flip()
        val cmd = buffer.getInt()
        val cmdSize = buffer.getInt()
        val cmdBodySize = cmdSize - LOAD_COMMAND_HEADER_SIZE_IN_BYTES
        if (cmd == LC_UUID) {
          patchUUID(channel, cmdBodySize)
          return@use
        }
        else {
          channel.position(channel.position() + cmdBodySize)
        }
      }
      context.messages.error("LC_UUID not found in $executable")
    }

    if (canBeSignedLocally) {
      runProcess(listOf("codesign", "--sign", "-", "--force", executable.toString()), inheritOut = true)
    }
  }

  private fun patchUUID(channel: SeekableByteChannel, cmdBodySize: Int) {
    val buffer = ByteBuffer.allocate(cmdBodySize).order(ByteOrder.BIG_ENDIAN)
    channel.read(buffer)
    buffer.flip()
    val mostSigBits = buffer.getLong()
    val leastSigBits = buffer.getLong()
    val currentUuid = UUID(mostSigBits, leastSigBits)
    context.messages.info("current UUID of $executable: $currentUuid")
    buffer.clear()
    val newUuid = customizer.getDistributionUUID(context, currentUuid)
    buffer.putLong(newUuid.mostSignificantBits)
    buffer.putLong(newUuid.leastSignificantBits)
    buffer.flip()
    if (context.isMacCodeSignEnabled || canBeSignedLocally) {
      channel.position(channel.position() - cmdBodySize)
      channel.write(buffer)
      context.messages.info("new UUID of $executable: $newUuid")
    }
  }
}
