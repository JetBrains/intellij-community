// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.cl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ShutDownTracker
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val BUFFER_SIZE = 8 * 1024 * 1024

private const val MAX_ACTIVATION_FRAMES = 40

private val CLASS_LOADING_MACHINERY_PREFIXES = arrayOf(
  "com.intellij.ide.plugins.cl.",
  "com.intellij.util.lang.",
  "java.lang.Class",
  "java.lang.invoke.",
  "java.lang.reflect.",
  "jdk.internal.",
)

/** The log of this process. The first loader state opens it, so no class of the log loads while the log is off. */
private val processLog: PluginClassLog? = PluginClassLog.open(System.getProperty("plugin.classloader.debug", ""))

/**
 * The state of a new loader of [pluginId] and [moduleName] in the log of this process, or `null` when the file cannot be
 * opened. A loader calls it only when `plugin.classloader.debug` is set.
 */
internal fun createPluginClassLogState(pluginId: String, moduleName: String?): PluginClassLogState? {
  return processLog?.let { PluginClassLogState(it, pluginId, moduleName) }
}

/**
 * The state of one plugin class loader in [log]: its owner and, until the exit, the stack of its first class.
 * A loader keeps it only when the log is on. The loader reaches the log only through this state: a field of the
 * type [PluginClassLog] on the class-loading path loads that class in compiled code, also with the log off.
 */
internal class PluginClassLogState(@JvmField val log: PluginClassLog, @JvmField val pluginId: String, @JvmField val moduleName: String?) {
  /** The id of the loader in the log, or -1 before its first record. The log reads and writes it under its lock. */
  @JvmField
  var id: Int = -1

  /**
   * The stack of the first class of the loader, until the log writes it at the exit. The loader keeps this state, so the
   * stack lives no longer than the loader. It keeps the classes of its frames.
   */
  @JvmField
  var stack: Throwable? = null
}

/**
 * The class log of the plugin class loaders that `-Dplugin.classloader.debug=<file>` turns on.
 * The format and its guarantees are in `community/platform/core-impl/spec/plugin-class-log.spec.md`.
 *
 * A record is copied into one byte buffer under one lock. The file is written only when the buffer is full, on a
 * define error, and at the exit. The stack of a first class is only filled on the class-loading path. Its conversion
 * to frames runs at the exit, because the conversion is the costly part of a stack.
 */
internal class PluginClassLog private constructor(private val channel: FileChannel, bufferSize: Int) {
  private val lock = ReentrantLock()
  private val buffer = ByteArray(bufferSize)
  private var position = 0
  private var nextLoaderId = 0
  private var isClosed = false
  private var records = 0L
  private var writeNanos = 0L
  private var stacks = 0L
  private var stackNanos = 0L
  private val statesWithStack = ArrayList<WeakReference<PluginClassLogState>>()

  companion object {
    /**
     * Opens the log at [path], the value of `plugin.classloader.debug`, and closes it at the exit.
     * Returns `null` for an empty [path] and when the file cannot be opened.
     */
    fun open(path: String): PluginClassLog? {
      if (path.isEmpty()) {
        return null
      }
      val file = if (path.startsWith("~/") || path.startsWith("~\\")) System.getProperty("user.home") + path.substring(1) else path
      try {
        val log = open(Paths.get(file))
        ShutDownTracker.getInstance().registerShutdownTask(log::close)
        return log
      }
      catch (e: IOException) {
        logger<PluginClassLog>().error(e)
        return null
      }
    }

    /** Opens the log at [file] and writes the header. The caller closes it. */
    fun open(file: Path, bufferSize: Int = BUFFER_SIZE): PluginClassLog {
      val channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
      val log = PluginClassLog(channel, bufferSize)
      log.putAscii("#plugin-classes\t2\n")
      return log
    }
  }

  /** The stack that loads the first class of a loader. Only the JVM fills it here, which is cheap. */
  fun captureStack(): Throwable {
    val start = System.nanoTime()
    val stack = Throwable()
    val duration = System.nanoTime() - start
    lock.withLock {
      stacks++
      stackNanos += duration
    }
    return stack
  }

  /**
   * Writes the `C` record of a class of the loader of [state], after the `L` record on the first record of the loader.
   * [stack] is the result of [captureStack] for the first class of the loader, else `null`.
   */
  fun classDefined(state: PluginClassLogState, className: String, findClassNanos: Long, stack: Throwable?) {
    val start = System.nanoTime()
    lock.withLock {
      if (isClosed) {
        return
      }
      val id = loaderId(state)
      putAscii("C\t")
      putInt(id)
      put('\t'.code)
      putLong(findClassNanos)
      put('\t'.code)
      putText(className)
      put('\n'.code)
      if (stack != null && state.stack == null) {
        state.stack = stack
        statesWithStack.add(WeakReference(state))
      }
      records++
      writeNanos += System.nanoTime() - start
    }
  }

  /** Writes the `E` record of a class that the loader of [state] failed to define, and writes the buffer to the file. */
  fun defineFailed(state: PluginClassLogState, className: String, message: String?) {
    lock.withLock {
      if (isClosed) {
        return
      }
      val id = loaderId(state)
      putAscii("E\t")
      putInt(id)
      put('\t'.code)
      putText(className)
      put('\t'.code)
      putText(message.orEmpty().replace('\n', ' ').replace('\t', ' '))
      put('\n'.code)
      flushBuffer()
    }
  }

  /** Writes the stacks of the live loaders and the `#cost` trailer, and closes the file. Later records are dropped. */
  fun close() {
    lock.withLock {
      if (isClosed) {
        return
      }
      isClosed = true
      // a loader that went has no stack in the log; its L and C records stay
      for (reference in statesWithStack) {
        val state = reference.get() ?: continue
        val stack = state.stack ?: continue
        state.stack = null
        putStack(state.id, stack)
      }
      statesWithStack.clear()
      putAscii("#cost\t")
      putLong(records)
      put('\t'.code)
      putLong(writeNanos)
      put('\t'.code)
      putLong(stacks)
      put('\t'.code)
      putLong(stackNanos)
      put('\n'.code)
      flushBuffer()
      try {
        channel.close()
      }
      catch (e: IOException) {
        logger<PluginClassLog>().error(e)
      }
    }
  }

  /** The id of the loader of [state]; the first record of a loader assigns it and writes the `L` record. Called under the lock. */
  private fun loaderId(state: PluginClassLogState): Int {
    val existing = state.id
    if (existing >= 0) {
      return existing
    }
    val id = nextLoaderId++
    state.id = id
    putAscii("L\t")
    putInt(id)
    put('\t'.code)
    putText(state.moduleName ?: "-")
    put('\t'.code)
    putText(state.pluginId)
    put('\n'.code)
    return id
  }

  /**
   * Writes the `S` records of the loader [id]: the frames of [stack] below the class-loading machinery, at most
   * [MAX_ACTIVATION_FRAMES]. Called under the lock at the exit.
   */
  private fun putStack(id: Int, stack: Throwable) {
    val frames = stack.stackTrace
    var first = 0
    while (first < frames.size && CLASS_LOADING_MACHINERY_PREFIXES.any { frames[first].className.startsWith(it) }) {
      first++
    }
    val end = minOf(frames.size, first + MAX_ACTIVATION_FRAMES)
    for (index in first until end) {
      val frame = frames[index]
      putAscii("S\t")
      putInt(id)
      put('\t'.code)
      putText(frame.className)
      put('.'.code)
      putText(frame.methodName)
      put('('.code)
      putText(frame.fileName ?: "Unknown Source")
      if (frame.lineNumber >= 0) {
        put(':'.code)
        putInt(frame.lineNumber)
      }
      putAscii(")\n")
    }
  }

  private fun ensureCapacity(size: Int) {
    if (position + size > buffer.size) {
      flushBuffer()
    }
  }

  private fun flushBuffer() {
    try {
      val data = ByteBuffer.wrap(buffer, 0, position)
      while (data.hasRemaining()) {
        channel.write(data)
      }
    }
    catch (_: IOException) {
    }
    position = 0
  }

  private fun put(byte: Int) {
    ensureCapacity(1)
    buffer[position++] = byte.toByte()
  }

  private fun putBytes(bytes: ByteArray) {
    if (bytes.size > buffer.size) {
      flushBuffer()
      try {
        val data = ByteBuffer.wrap(bytes)
        while (data.hasRemaining()) {
          channel.write(data)
        }
      }
      catch (_: IOException) {
      }
      return
    }
    ensureCapacity(bytes.size)
    System.arraycopy(bytes, 0, buffer, position, bytes.size)
    position += bytes.size
  }

  /** Writes [text] that holds ASCII only. */
  private fun putAscii(text: String) {
    if (text.length > buffer.size) {
      putBytes(text.toByteArray(Charsets.US_ASCII))
      return
    }
    ensureCapacity(text.length)
    for (char in text) {
      buffer[position++] = char.code.toByte()
    }
  }

  /** Writes [text] as UTF-8, one byte per char while the chars are ASCII. */
  private fun putText(text: String) {
    for (char in text) {
      if (char.code >= 0x80) {
        putBytes(text.toByteArray(Charsets.UTF_8))
        return
      }
    }
    putAscii(text)
  }

  private fun putInt(value: Int) {
    putLong(value.toLong())
  }

  /** Writes a non-negative [value] as ASCII digits. */
  private fun putLong(value: Long) {
    ensureCapacity(20)
    if (value <= 0L) {
      buffer[position++] = '0'.code.toByte()
      return
    }
    var digits = 0
    var rest = value
    while (rest > 0L) {
      digits++
      rest /= 10
    }
    rest = value
    var index = position + digits - 1
    while (rest > 0L) {
      buffer[index--] = ('0'.code + (rest % 10).toInt()).toByte()
      rest /= 10
    }
    position += digits
  }
}
