@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps.dependencies

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ZipFile
import io.netty.buffer.Unpooled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import org.jetbrains.bazel.jvm.emptySet
import org.jetbrains.bazel.jvm.jps.output.ABI_IC_NODE_FORMAT_VERSION
import org.jetbrains.bazel.jvm.jps.output.NODE_INDEX_FILENAME
import org.jetbrains.bazel.jvm.jps.output.NodeIndex
import org.jetbrains.bazel.jvm.jps.output.NodeIndexEntry
import org.jetbrains.bazel.jvm.jps.output.readNodeIndex
import org.jetbrains.bazel.jvm.jps.state.DependencyDescriptor
import org.jetbrains.bazel.jvm.orEmpty
import org.jetbrains.jps.dependency.java.JvmClass
import org.jetbrains.jps.dependency.storage.NettyBufferGraphDataInput
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class DependencyAnalyzer(private val coroutineScope: CoroutineScope) {
  private val cache: AsyncCache<DependencyDescriptor, Pair<ScatterMap<AbiJarSource, NodeUpdateItem>, ScatterMap<AbiJarSource, NodeUpdateItem>>> = Caffeine.newBuilder()
    .expireAfterAccess(2.minutes.toJavaDuration())
    .maximumSize(100)
    .executor { coroutineScope.launch { it.run() } }
    .buildAsync()

  internal suspend fun computeForChanged(
    dependencyDescriptor: DependencyDescriptor,
    oldFile: Path,
    newFile: Path,
    fileBazelDigestHash: Long,
  ): Pair<ScatterMap<AbiJarSource, NodeUpdateItem>, ScatterMap<AbiJarSource, NodeUpdateItem>> {
    return cache.get(dependencyDescriptor) { dependencyDescriptor ->
      doCompute(
        dependencyDescriptor = dependencyDescriptor,
        newFile = newFile,
        fileBazelDigestHash = fileBazelDigestHash,
        oldFile = oldFile,
      )
    }.asDeferred().await()
  }
}

private fun doCompute(
  dependencyDescriptor: DependencyDescriptor,
  newFile: Path,
  fileBazelDigestHash: Long,
  oldFile: Path
): Pair<ScatterMap<AbiJarSource, NodeUpdateItem>, ScatterMap<AbiJarSource, NodeUpdateItem>> {
  val changedOrAddedNodes = MutableScatterMap<AbiJarSource, NodeUpdateItem>()
  val deletedNodes = MutableScatterMap<AbiJarSource, NodeUpdateItem>()

  if (dependencyDescriptor.oldDigest == null) {
    ZipFile.load(newFile).use { newZipFile ->
      val newNodeIndex = loadNodeIndex(newZipFile)
      newNodeIndex.map.forEach { path, newNodeInfo ->
        val newNode = readIcNode(newZipFile, newNodeInfo)
        val source = AbiJarSource(
          fileBazelDigestHash = fileBazelDigestHash,
          innerPathHash = path,
          oldNodes = emptySet(),
          newNodes = java.util.Set.of(newNode),
        )
        val item = NodeUpdateItem(newNode = newNode, oldNode = null, source = source)
        changedOrAddedNodes.put(source, item)
      }
    }
  }
  else {
    doComputeDiffForChangedAbiJar(
      oldFile = oldFile,
      newFile = dependencyDescriptor.file,
      fileBazelDigestHash = fileBazelDigestHash,
      changedOrAddedNodes = changedOrAddedNodes,
      deletedNodes = deletedNodes,
    )
  }

  return changedOrAddedNodes.orEmpty() to deletedNodes.orEmpty()
}

private fun doComputeDiffForChangedAbiJar(
  oldFile: Path,
  newFile: Path,
  fileBazelDigestHash: Long,
  changedOrAddedNodes: MutableScatterMap<AbiJarSource, NodeUpdateItem>,
  deletedNodes: MutableScatterMap<AbiJarSource, NodeUpdateItem>
) {
  ZipFile.load(oldFile).use { oldZipFile ->
    ZipFile.load(newFile).use { newZipFile ->
      val oldNodeIndex = loadNodeIndex(oldZipFile)
      val newNodeIndex = loadNodeIndex(newZipFile)

      // find changed or added nodes
      newNodeIndex.map.forEach { path, newNodeInfo ->
        val oldNodeInfo = oldNodeIndex.map.get(path)
        if (oldNodeInfo == null || oldNodeInfo.digest != newNodeInfo.digest) {
          val oldNode = if (oldNodeInfo == null) null else readIcNode(oldZipFile, oldNodeInfo)
          val newNode = readIcNode(newZipFile, newNodeInfo)
          val source = AbiJarSource(
            fileBazelDigestHash = fileBazelDigestHash,
            innerPathHash = path,
            oldNodes = if (oldNode == null) emptySet() else java.util.Set.of(oldNode),
            newNodes = java.util.Set.of(newNode),
          )
          val item = NodeUpdateItem(newNode = newNode, oldNode = oldNode, source = source)
          changedOrAddedNodes.put(source, item)
        }
      }
      // find deleted nodes
      oldNodeIndex.map.forEach { path, oldNodeInfo ->
        if (newNodeIndex.map.containsKey(path)) {
          return@forEach
        }

        val oldNode = readIcNode(oldZipFile, oldNodeInfo)
        val source = AbiJarSource(
          fileBazelDigestHash = fileBazelDigestHash,
          innerPathHash = path,
          oldNodes = java.util.Set.of(oldNode),
          newNodes = emptySet(),
        )
        val item = NodeUpdateItem(newNode = null, oldNode = oldNode, source = source)
        deletedNodes.put(source, item)
      }
    }
  }
}

private fun readIcNode(zipFile: ZipFile, nodeInfo: NodeIndexEntry): JvmClass {
  return readNode((zipFile as ImmutableZipFile).__getRawSlice().slice(nodeInfo.offset, nodeInfo.size))
}

private fun readNode(data: ByteBuffer): JvmClass {
  val byteBuf = Unpooled.wrappedBuffer(data)
  val formatVersion = byteBuf.readIntLE()
  if (formatVersion != ABI_IC_NODE_FORMAT_VERSION) {
    throw RuntimeException("Unsupported ABI IC node format version: $formatVersion")
  }
  val input = NettyBufferGraphDataInput(byteBuf)
  val node = JvmClass(input)
  return node
}

private fun loadNodeIndex(file: ZipFile): NodeIndex {
  return readNodeIndex(requireNotNull(file.getByteBuffer(NODE_INDEX_FILENAME)) {
    "Cannot find node index in zip file: $file"
  })
}