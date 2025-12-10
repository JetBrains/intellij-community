// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import org.jetbrains.annotations.ApiStatus.Internal
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer

@Internal
class BuildOutputLineDispatcher<Payload>(
  private val lineConsumer: BiConsumer<String, List<Payload>>
) {

  private val buffer = AtomicReference(BuildOutputBuffer<Payload>())

  fun notifyTextAvailable(text: CharSequence, payload: Payload) {
    val lines = text.split('\n')
    for (line in lines.dropLast(1)) {
      notifyLineOrPrefixAvailable(line.removeSuffix("\r"), isLine = true, payload)
    }
    val linePrefix = lines.last()
    if (linePrefix.isNotEmpty()) {
      notifyLineOrPrefixAvailable(linePrefix, isLine = false, payload)
    }
  }

  private fun notifyLineOrPrefixAvailable(text: CharSequence, isLine: Boolean, payload: Payload) {
    val buffer = buffer.get() ?: throw IllegalStateException("The line processor was closed")
    buffer.text.append(text)
    buffer.payload.add(payload)
    if (isLine) {
      buffer.flushBuffer()
    }
  }

  fun close() {
    buffer.getAndSet(null)
      ?.flushBuffer()
  }

  private fun BuildOutputBuffer<Payload>.flushBuffer() {
    lineConsumer.accept(text.toString(), payload.toList())
    text.setLength(0)
    payload.clear()
  }

  private class BuildOutputBuffer<Payload> {
    val text = StringBuilder()
    val payload = ArrayList<Payload>()
  }
}