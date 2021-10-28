// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.fasterxml.jackson.jr.ob.JSON
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.FileHash
import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.compressDir
import org.jetbrains.intellij.build.io.writeNewFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.zip.Deflater

private const val algorithm = "SHA-256"

@Suppress("unused")
fun bulkZipWithPrefix(commonSourceDir: Path, items: List<Map.Entry<String, Path>>, compress: Boolean) {
  tracer.spanBuilder("archive directories")
    .setAttribute(AttributeKey.longKey("count"), items.size.toLong())
    .setAttribute(AttributeKey.stringKey("commonSourceDir"), commonSourceDir.toString())
    .startSpan()
    .use { parentSpan ->
      val json = JSON.std.without(JSON.Feature.USE_FIELDS)
      ForkJoinTask.invokeAll(items.map { item ->
        ForkJoinTask.adapt {
          parentSpan.makeCurrent().use {
            val target = item.value
            val dir = commonSourceDir.resolve(item.key)
            tracer.spanBuilder("build plugin archive")
              .setAttribute("inputDir", dir.toString())
              .setAttribute("outputFile", target.toString())
              .startSpan()
              .use {
                writeNewFile(target) { channel ->
                  ZipArchiver(ZipFileWriter(channel, compress)).use { archiver ->
                    archiver.setRootDir(dir, item.key)
                    compressDir(dir, archiver, excludes = null)
                  }
                }
              }
            if (compress) {
              tracer.spanBuilder("build plugin blockmap")
                .setAttribute("file", target.toString())
                .startSpan()
                .use {
                  buildBlockMap(target, json)
                }
            }
          }
        }
      })
    }
}

/**
 * Builds a blockmap and hash files for plugin to provide downloading plugins via incremental downloading algorithm Blockmap.
 */
internal fun buildBlockMap(file: Path, json: JSON) {
  val bytes = Files.newInputStream(file).use { input ->
    json.asBytes(BlockMap(input, algorithm))
  }

  val fileParent = file.parent
  val fileName = file.fileName.toString()
  writeNewFile(fileParent.resolve("$fileName.blockmap.zip")) { channel ->
    val zipCreator = ZipFileWriter(channel, Deflater(Deflater.DEFAULT_COMPRESSION, true))
    zipCreator.writeCompressed("blockmap.json", bytes)
    zipCreator.finish()
  }

  val hashFile = fileParent.resolve("$fileName.hash.json")
  Files.newInputStream(file).use { input ->
    Files.newOutputStream(hashFile).use { output ->
      json.write(FileHash(input, algorithm), output)
    }
  }
}