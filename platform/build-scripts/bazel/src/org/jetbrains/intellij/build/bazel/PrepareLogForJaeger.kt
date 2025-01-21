// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.text.NaturalComparator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.useLines

internal class PrepareLogForJaeger {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val dir = Path.of("/private/var/tmp/_bazel_develar/7e07e651bab53fecab4e28dcdfe77e05/bazel-workers")
      val files = Files.newDirectoryStream(dir).use { stream ->
        stream.filter {
          val p = it.toString()
          p.endsWith(".log") && !p.contains("Javac")
        }
      }
        .sortedWith { o1, o2 -> NaturalComparator.INSTANCE.compare(o1.fileName.toString(), o2.fileName.toString()) }
      for (file in files) {
        val out = Files.newBufferedWriter(dir.resolve(file.fileName.toString().replace(".log", ".jsonl")))
        out.use {
          file.useLines { lines ->
            for (line in lines) {
              if (!(line.startsWith('{'))) {
                continue
              }

              out.appendLine(line)
            }
          }
        }
      }
    }
  }
}