@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.output

import androidx.collection.MutableLongObjectMap
import androidx.collection.MutableScatterSet
import com.intellij.util.lang.HashMapZipFile
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder
import org.jetbrains.jps.dependency.storage.NettyBufferGraphDataOutput
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.org.objectweb.asm.ClassReader
import java.io.File
import java.nio.ByteOrder
import java.nio.file.Path
import java.util.*

internal const val NODE_INDEX_FILENAME = "__node_index__"
private val NODE_INDEX_FILENAME_BYTES = "__node_index__".toByteArray()

internal class IncrementalAbiHelper(
  private val abiFileToData: TreeMap<String, Any>,
  internal val oldAbiZipFile: HashMapZipFile?,
) {
  private val classesToBeDeleted = MutableScatterSet<String>()

  // IncrementalAbiHelper is created _before_ we check that we need to recompile something, so, read node index only if needed
  private val nodeIndex: Lazy<NodeIndex>

  init {
    if (oldAbiZipFile == null) {
      nodeIndex = lazyOf(NodeIndex(MutableLongObjectMap()))
    }
    else {
      val data = oldAbiZipFile.getByteBuffer(NODE_INDEX_FILENAME)
        ?: throw RebuildRequestedException(IllegalStateException("old abi file is provided ($oldAbiZipFile) " +
          "but does not contain $NODE_INDEX_FILENAME"))
      nodeIndex = lazy(LazyThreadSafetyMode.NONE) {
        readNodeIndex(data)
      }
    }
  }

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
    nodeIndex.value.remove(path)
    return abiFileToData.remove(path) != null
  }

  fun write(abiJar: Path) {
    val nodeIndex = nodeIndex.value
    val packageIndexBuilder = PackageIndexBuilder(writeCrc32 = false)
    writeZipUsingTempFile(file = abiJar, indexWriter = packageIndexBuilder.indexWriter) { stream ->
      doWriteToZip(
        oldZipFile = oldAbiZipFile,
        fileToData = abiFileToData,
        packageIndexBuilder = null,
        stream = stream,
        oldDataProcessor = f@ { path, pathBytes ->
          if (!path.endsWith(".class") || path.startsWith("META-INF/")) {
            return@f
          }

          val pathHash = pathToKey(pathBytes)
          val nodeInfo = requireNotNull(nodeIndex.getInfo(pathHash)) {
            "Cannot find old ABI IC node for path $path, corrupted ABI?"
          }
          stream.writeUndeclaredData { buffer, offsetInFile ->
            val start = nodeInfo.offset
            val size = nodeInfo.size
            val slice = oldAbiZipFile!!.__getRawSlice().slice(start, size)
            slice.order(ByteOrder.LITTLE_ENDIAN)
            require(slice.getInt(0) == ABI_IC_NODE_FORMAT_VERSION) {
              "Incorrect slice for path $path, corrupted ABI?"
            }

            buffer.writeBytes(slice)

            nodeIndex.updateOffset(pathHash, offsetInFile, oldNodeIndexEntry = nodeInfo)
          }
        },
        newDataProcessor = f@ { data, path, pathBytes ->
          // we must write kotlin_module
          if (!path.endsWith(".class") || path.startsWith("META-INF/")) {
            return@f
          }

          val pathHash = pathToKey(pathBytes)

          val reader = ClassReader(data)
          val node = JvmClassNodeBuilder.createForLibrary(filePath = path, classReader = reader).build(
            isLibraryMode = true,
            // here path to class, not to the node
            outFilePathHash = pathHash,
            skipPrivateMethodsAndFields = false,
          )

          stream.writeUndeclaredData { buffer, offsetInFile ->
            val wI = buffer.writerIndex()
            buffer.writeIntLE(ABI_IC_NODE_FORMAT_VERSION)
            node.write(NettyBufferGraphDataOutput(buffer))
            nodeIndex.put(pathHash, data, offsetInFile, buffer.writerIndex() - wI)
          }
        },
      )

      abiFileToData.clear()
      // now, close the old file before writing to it
      oldAbiZipFile?.close()

      // write node index after ^^^ to reduce memory usage
      stream.write(NODE_INDEX_FILENAME_BYTES, estimatedSize = nodeIndex.serializedSize()) { buffer ->
        nodeIndex.write(buffer)
      }

      packageIndexBuilder.writePackageIndex(stream = stream, addDirEntriesMode = AddDirEntriesMode.NONE)
    }
  }

  fun close() {
    oldAbiZipFile?.close()
  }
}