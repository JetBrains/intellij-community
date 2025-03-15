@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.output

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.util.lang.HashMapZipFile
import io.netty.buffer.ByteBufAllocator
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.intellij.build.io.use
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder
import org.jetbrains.jps.dependency.storage.NettyBufferGraphDataOutput
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.org.objectweb.asm.ClassReader
import java.io.File
import java.nio.file.Path
import java.util.*

internal class IncrementalAbiHelper(
  private val abiFileToData: TreeMap<String, Any>,
  internal val oldAbiZipFile: HashMapZipFile?,
) {
  private val classesToBeDeleted = hashSet<String>()

  fun createAbiForJava(path: String, data: ByteArray) {
    val abiData = org.jetbrains.bazel.jvm.abi.createAbiForJava(data, classesToBeDeleted) ?: return
    abiFileToData.put(path, abiData)
  }

  @Suppress("DuplicatedCode")
  fun registerKotlincAbiOutput(outputFiles: List<OutputFile>, isAlreadyMarkedAsChanged: Boolean): Boolean {
    var isChanged = isAlreadyMarkedAsChanged
    val abiFileToData = abiFileToData
    for (file in outputFiles) {
      // not clear - is path system-independent or not?
      val path = file.relativePath.replace(File.separatorChar, '/')
      val newContent = file.asByteArray()
      val oldContent = abiFileToData.put(path, newContent) as? ByteArray
      if (!isChanged && (oldContent == null || !oldContent.contentEquals(newContent))) {
        isChanged = true
      }
    }
    return isChanged
  }

  fun remove(path: String): Boolean {
    return abiFileToData.remove(abiClassPathToIncrementalNodePath(path)) != null
  }

  fun write(abiJar: Path) {
    writeZipUsingTempFile(file = abiJar, indexWriter = null) { stream ->
      doWriteToZip(
        oldZipFile = oldAbiZipFile,
        fileToData = abiFileToData,
        packageIndexBuilder = null,
        stream = stream,
        newDataProcessor = f@ { data, path, pathBytes ->
          // we must write kotlin_module
          if (!path.endsWith(".class") || path.startsWith("META-INF/")) {
            return@f
          }

          val reader = ClassReader(data)
          val node = JvmClassNodeBuilder.createForLibrary(filePath = path, classReader = reader).build(
            isLibraryMode = true,
            // here path to class, not to the node
            outFilePathHash = Hashing.xxh3_64().hashBytesToLong(pathBytes),
            skipPrivateMethodsAndFields = false,
          )

          val abiPathBytes = abiClassPathToIncrementalNodePath(path).toByteArray()
          ByteBufAllocator.DEFAULT.directBuffer(1024).use { buffer ->
            val headerSize = 30 + abiPathBytes.size
            buffer.writerIndex(headerSize)

            buffer.writeIntLE(ABI_IC_NODE_FORMAT_VERSION)
            node.write(NettyBufferGraphDataOutput(buffer))

            stream.writeEntryWithHalfBackedBuffer(path = abiPathBytes, unwrittenHeaderAndData = buffer)
          }
        },
      )

      abiFileToData.clear()
      // now, close the old file before writing to it
      oldAbiZipFile?.close()
    }
  }

  fun close() {
    oldAbiZipFile?.close()
  }
}

private fun abiClassPathToIncrementalNodePath(path: String): String = "$path.n"