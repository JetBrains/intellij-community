// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.worker.state

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.ipc.ArrowFileReader
import org.apache.arrow.vector.ipc.ArrowFileWriter
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.FieldType
import org.jetbrains.intellij.build.io.writeFileUsingTempFile
import java.nio.channels.FileChannel
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.EnumSet

internal val notNullUtfStringFieldType = FieldType.notNullable(ArrowType.Utf8.INSTANCE)

private val WRITE_FILE_OPTION = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE)
private val READ_FILE_OPTION = EnumSet.of(StandardOpenOption.READ)

internal fun writeVectorToFile(file: Path, root: VectorSchemaRoot, metadata: Map<String, String>) {
  writeFileUsingTempFile(file) { tempFile ->
    FileChannel.open(tempFile, WRITE_FILE_OPTION).use { fileChannel ->
      ArrowFileWriter(root, null, fileChannel, metadata).use { fileWriter ->
        fileWriter.start()
        fileWriter.writeBatch()
        fileWriter.end()
      }
    }
  }
}

internal inline fun <T : Any> readArrowFile(
  file: Path,
  allocator: RootAllocator,
  parentSpan: Span?,
  crossinline task: (ArrowFileReader) -> T,
): T? {
  try {
    return FileChannel.open(file, READ_FILE_OPTION).use { fileChannel ->
      ArrowFileReader(fileChannel, allocator).use { fileReader ->
        // metadata is available only after loading batch
        fileReader.loadNextBatch()
        task(fileReader)
      }
    }
  }
  catch (_: NoSuchFileException) {
    return null
  }
  catch (e: Throwable) {
    if (parentSpan == null) {
      throw e
    }

    parentSpan.recordException(e, Attributes.of(
      AttributeKey.stringKey("message"), "cannot load build state file",
      AttributeKey.stringKey("stateFile"), file.toString(),
    ))
    // will be deleted by caller
    return null
  }
}
