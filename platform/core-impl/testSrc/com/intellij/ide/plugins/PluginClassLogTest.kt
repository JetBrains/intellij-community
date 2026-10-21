// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLog
import com.intellij.ide.plugins.cl.PluginClassLogState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * The writer of the plugin class log. The reader of `vm bench` parses the same records, so a change of a record shape
 * changes `community/tools/vm/crates/avl-vm/src/bench/pluginlog.rs` too.
 *
 * The test lives outside `com.intellij.ide.plugins.cl`, because the log drops the frames of that package from a stack.
 */
internal class PluginClassLogTest {
  @TempDir
  lateinit var dir: Path

  private fun read(file: Path): List<String> = Files.readAllLines(file)

  @Test
  fun `a loader has one L record before its C records and a stack at the exit`() {
    val file = dir.resolve("plugin-classes.txt")
    val log = PluginClassLog.open(file)
    val main = PluginClassLogState(log = log, pluginId = "Lombook Plugin", moduleName = null)
    val module = PluginClassLogState(log = log, pluginId = "com.intellij.air", moduleName = "intellij.air.frontend.prompt.ui")
    log.classDefined(main, "a.First", findClassNanos = 1200, stack = log.captureStack())
    log.classDefined(module, "b.First", findClassNanos = 0, stack = log.captureStack())
    log.classDefined(main, "a.Second", findClassNanos = 34, stack = null)
    log.close()

    val lines = read(file)
    assertEquals(
      listOf(
        "#plugin-classes\t2",
        "L\t0\t-\tLombook Plugin",
        "C\t0\t1200\ta.First",
        "L\t1\tintellij.air.frontend.prompt.ui\tcom.intellij.air",
        "C\t1\t0\tb.First",
        "C\t0\t34\ta.Second",
      ),
      lines.take(6),
    )
    val stackOfMain = lines.filter { it.startsWith("S\t0\t") }
    assertTrue(stackOfMain.first().startsWith("S\t0\t${PluginClassLogTest::class.java.name}."), stackOfMain.first())
    assertTrue(stackOfMain.size <= 40, "at most 40 frames, got ${stackOfMain.size}")
    assertTrue(lines.any { it.startsWith("S\t1\t") }, "the second loader has its stack")
    val trailer = lines.last().split('\t')
    assertEquals(listOf("#cost", "3"), trailer.take(2))
    assertEquals("2", trailer[3])
    assertEquals(lines.size - 1, lines.indexOfFirst { it.startsWith("#cost") }, "the trailer is the last line")
  }

  @Test
  fun `a define error is one E record with the message on one line`() {
    val file = dir.resolve("plugin-classes.txt")
    val log = PluginClassLog.open(file)
    val state = PluginClassLogState(log = log, pluginId = "p", moduleName = "p.m")
    log.defineFailed(state, "a.Broken", "line one\nline\ttwo")
    log.close()

    assertEquals(listOf("#plugin-classes\t2", "L\t0\tp.m\tp", "E\t0\ta.Broken\tline one line two"), read(file).take(3))
  }

  @Test
  fun `a record after the close is dropped`() {
    val file = dir.resolve("plugin-classes.txt")
    val log = PluginClassLog.open(file)
    log.close()
    log.classDefined(PluginClassLogState(log = log, pluginId = "p", moduleName = null), "a.Late", findClassNanos = 1, stack = null)

    assertEquals(listOf("#plugin-classes\t2", "#cost\t0\t0\t0\t0"), read(file))
  }

  @Test
  fun `a full buffer writes the file and keeps every record, also UTF-8 text`() {
    val file = dir.resolve("plugin-classes.txt")
    val log = PluginClassLog.open(file, bufferSize = 64)
    val state = PluginClassLogState(log = log, pluginId = "плагин", moduleName = null)
    val names = (0 until 200).map { "a.Класс$it" }
    for (name in names) {
      log.classDefined(state, name, findClassNanos = 7, stack = null)
    }
    log.close()

    val lines = read(file)
    assertEquals("L\t0\t-\tплагин", lines[1])
    assertEquals(names.map { "C\t0\t7\t$it" }, lines.filter { it.startsWith("C\t") })
  }

  @Test
  fun `records of many threads stay whole lines`() {
    val file = dir.resolve("plugin-classes.txt")
    val log = PluginClassLog.open(file, bufferSize = 4096)
    val threadCount = 8
    val classCount = 2000
    val states = (0 until threadCount).map { PluginClassLogState(log = log, pluginId = "p$it", moduleName = "m$it") }
    val start = CountDownLatch(1)
    val threads = states.mapIndexed { index, state ->
      thread {
        start.await()
        for (classIndex in 0 until classCount) {
          log.classDefined(state, "c$index.C$classIndex", findClassNanos = classIndex.toLong(), stack = null)
        }
      }
    }
    start.countDown()
    threads.forEach { it.join() }
    log.close()

    val lines = read(file)
    assertEquals(threadCount, lines.count { it.startsWith("L\t") })
    val classes = lines.filter { it.startsWith("C\t") }
    assertEquals(threadCount * classCount, classes.size)
    assertTrue(classes.all { it.split('\t').size == 4 }, "every C record has four fields")
    for (state in states) {
      val first = lines.indexOfFirst { it.startsWith("C\t${state.id}\t") }
      assertTrue(lines.indexOf("L\t${state.id}\t${state.moduleName}\t${state.pluginId}") in 0 until first, "L comes before the first C of ${state.pluginId}")
    }
  }
}
