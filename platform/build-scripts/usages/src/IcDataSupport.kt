// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.usages

import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.NodeSourcePathMapper
import org.jetbrains.jps.dependency.impl.GraphDataInputImpl
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl
import org.jetbrains.jps.dependency.impl.GraphImpl
import org.jetbrains.jps.dependency.impl.PathSource
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

internal object IcDataPaths {
  const val CONFIG_STATE_FILE_NAME = "config-state.dat"
  const val DEP_GRAPH_FILE_NAME = "dep-graph.mv"
  const val ABI_JAR_SUFFIX = ".abi.jar"
  const val DATA_DIR_NAME_SUFFIX = "-ic"

  fun truncateExtension(filename: String): String {
    val index = filename.lastIndexOf('.')
    return if (index >= 0) filename.substring(0, index) else filename
  }
}

internal object IcConfigurationStateIO {
  private const val VERSION = 8

  fun loadLibraryClasspath(configFile: Path, pathMapper: NodeSourcePathMapper): List<Path>? {
    return try {
      DataInputStream(InflaterInputStream(Files.newInputStream(configFile))).use { stream ->
        val input = GraphDataInputImpl.wrap(stream)
        if (input.readInt() != VERSION) {
          return null
        }
        skipSourceSnapshot(input)
        skipResourceGroups(input)
        val libraries = readSourceSnapshot(input)
        input.readLong()
        input.readLong()
        input.readLong()
        libraries.map { pathMapper.toPath(it).normalize() }
      }
    }
    catch (_: Throwable) {
      null
    }
  }

  fun writeConfigState(configFile: Path, classpathEntries: List<Path>, pathMapper: NodeSourcePathMapper) {
    Files.createDirectories(configFile.parent)
    DataOutputStream(DeflaterOutputStream(Files.newOutputStream(configFile), Deflater(Deflater.BEST_SPEED))).use { stream ->
      val output = GraphDataOutputImpl.wrap(stream)
      output.writeInt(VERSION)
      writeSourceSnapshot(output, emptyList())
      output.writeInt(0)
      writeSourceSnapshot(output, classpathEntries.map(pathMapper::toNodeSource))
      output.writeLong(0)
      output.writeLong(0)
      output.writeLong(0)
    }
  }

  private fun skipResourceGroups(input: GraphDataInput) {
    repeat(input.readInt()) {
      skipSourceSnapshot(input)
      input.readUTF()
      input.readUTF()
    }
  }

  private fun skipSourceSnapshot(input: GraphDataInput) {
    repeat(input.readInt()) {
      input.readUTF()
      PathSource(input)
    }
  }

  private fun readSourceSnapshot(input: GraphDataInput): List<NodeSource> {
    val count = input.readInt()
    val result = ArrayList<NodeSource>(count)
    repeat(count) {
      input.readUTF()
      result.add(PathSource(input))
    }
    return result
  }

  private fun writeSourceSnapshot(output: GraphDataOutput, sources: List<NodeSource>) {
    output.writeInt(sources.size)
    for (source in sources) {
      output.writeUTF("")
      source.write(output)
    }
  }
}

internal inline fun <T : GraphImpl, R> T.useGraph(block: (T) -> R): R {
  try {
    return block(this)
  }
  finally {
    close()
  }
}
