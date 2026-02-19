// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import java.nio.file.Path
import java.util.TreeMap
import kotlin.experimental.and

class ClassVersionPrinter {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val versionToClasses = TreeMap<Byte, MutableList<String>>()
      readZipFile(Path.of(args[0])) { name, dataSupplier ->
        if (!name.endsWith(".class")) {
          return@readZipFile ZipEntryProcessorResult.CONTINUE
        }

        val buffer = dataSupplier()
        check(buffer.getInt() == 0xCAFEBABE.toInt()) {
          "$name: invalid .class file header"
        }

        buffer.position(buffer.position() + 3)

        val major = buffer.get() and 0xff.toByte()
        check(major > 44 || major < 100) {
          "$name: suspicious .class file version: $major"
        }

        versionToClasses.computeIfAbsent(major) { mutableListOf() }.add(name)
        ZipEntryProcessorResult.CONTINUE
      }

      for ((major, names) in versionToClasses) {
        val prefix = "$major (${major - 44})"
        val separator = "\n  $prefix  "
        println("${"-".repeat(40)} $prefix ${"-".repeat(40)}$separator${names.joinToString(separator = separator)}")
      }
    }
  }
}