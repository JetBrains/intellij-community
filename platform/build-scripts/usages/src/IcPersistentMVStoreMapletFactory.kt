// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.usages

import org.h2.mvstore.DataUtils
import org.h2.mvstore.FileStore
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.BasicDataType
import org.h2.mvstore.type.DataType
import org.jetbrains.jps.dependency.ComparableTypeExternalizer
import org.jetbrains.jps.dependency.Enumerator
import org.jetbrains.jps.dependency.Maplet
import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.impl.CachingMaplet
import org.jetbrains.jps.dependency.impl.CachingMultiMaplet
import org.jetbrains.jps.dependency.impl.GraphDataInputImpl
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl
import org.jetbrains.jps.dependency.impl.GraphElementInterner
import org.jetbrains.jps.dependency.impl.ObjectEnumerator
import java.io.Closeable
import java.io.DataInput
import java.io.DataOutput
import java.io.EOFException
import java.io.Flushable
import java.io.IOException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.min

internal class IcPersistentMVStoreMapletFactory(filePath: String, maxBuilderThreads: Int) : MapletFactory, Closeable, Flushable {
  private val store: MVStore
  private val initialVersion: Long
  private val enumerator: MVSEnumerator
  private val cacheSize: Int
  private val dataInterner: (Any?) -> Any? = { element ->
    when (element) {
      is Usage -> GraphElementInterner.intern(element)
      is ReferenceID -> GraphElementInterner.intern(element)
      else -> element
    }
  }

  init {
    Files.createDirectories(Path.of(filePath).parent)
    store = MVStore.Builder()
      .fileName(filePath)
      .autoCommitDisabled()
      .cacheSize(8)
      .compress()
      .cacheConcurrency(getConcurrencyLevel(maxBuilderThreads))
      .open()
    store.setVersionsToKeep(0)
    initialVersion = store.currentVersion
    enumerator = MVSEnumerator(store)

    val maxGb = (Runtime.getRuntime().maxMemory() / 1_073_741_824L).toInt()
    cacheSize = BASE_CACHE_SIZE * maxGb.coerceIn(1, 5)
  }

  override fun <K, V> createSetMultiMaplet(
    storageName: String,
    keyExternalizer: ComparableTypeExternalizer<K>,
    valueExternalizer: ComparableTypeExternalizer<V>,
  ): MultiMaplet<K, V> {
    val maplet = PersistentMVStoreSetMultiMaplet(
      store = store,
      mapName = storageName,
      keyType = GraphDataType(keyExternalizer, enumerator, dataInterner),
      valueType = GraphDataType(valueExternalizer, enumerator, dataInterner),
    )
    return CachingMultiMaplet(maplet, cacheSize)
  }

  override fun <K, V> createMaplet(
    storageName: String,
    keyExternalizer: ComparableTypeExternalizer<K>,
    valueExternalizer: ComparableTypeExternalizer<V>,
  ): Maplet<K, V> {
    val maplet = PersistentMVStoreSimpleMaplet(
      store = store,
      mapName = storageName,
      keyType = GraphDataType(keyExternalizer, enumerator, dataInterner),
      valueType = GraphDataType(valueExternalizer, enumerator, dataInterner),
    )
    return CachingMaplet(maplet, cacheSize)
  }

  override fun close() {
    store.commit()
    enumerator.flush()
    store.close(compactionTimeMs())
  }

  override fun flush() {
    if (store.tryCommit() >= 0L && enumerator.flush()) {
      store.commit()
    }
  }

  private fun compactionTimeMs(): Int {
    val fileStore: FileStore<*> = store.fileStore
    val fileFillRate = fileStore.fillRate
    val chunkFillRate = fileStore.chunksFillRate
    return when {
      fileFillRate > 80 && chunkFillRate > 80 -> 0
      fileFillRate > 60 && chunkFillRate > 60 -> 100
      else -> 300
    }
  }

  private class GraphDataType<T>(
    private val externalizer: ComparableTypeExternalizer<T>,
    private val enumerator: Enumerator?,
    private val objectInterner: ((Any?) -> Any?)?,
  ) : BasicDataType<T>() {
    override fun compare(a: T, b: T): Int = externalizer.compare(a, b)

    override fun isMemoryEstimationAllowed(): Boolean = false

    override fun getMemory(obj: T): Int = 0

    override fun write(buff: WriteBuffer, value: T) {
      try {
        externalizer.save(GraphDataOutputImpl.wrap(WriteBufferDataOutput(buff), enumerator), value)
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }
    }

    override fun read(buff: ByteBuffer): T {
      try {
        return externalizer.load(GraphDataInputImpl.wrap(ByteBufferDataInput(buff), enumerator, objectInterner))
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }
    }

    @Suppress("UNCHECKED_CAST")
    override fun createStorage(size: Int): Array<T> = externalizer.createStorage(size)
  }

  private class MVSEnumerator(store: MVStore) : Enumerator {
    private val storeMap: MVMap<Int, String> = store.openMap("string-table")
    private val enumerator = ObjectEnumerator(storeMap.entries.map { it.value }, GraphElementInterner::intern)

    @Synchronized
    override fun toString(num: Int): String {
      return enumerator.lookup(num)
             ?: throw IOException("Mapping for number $num does not exist. Current string table size: ${enumerator.tableSize} entries.")
    }

    @Synchronized
    override fun toNumber(str: String): Int = enumerator.toNumber(str)

    @Synchronized
    fun flush(): Boolean {
      return try {
        enumerator.drainUnsaved { key, value -> storeMap[key] = value }
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }
    }
  }

  private companion object {
    const val BASE_CACHE_SIZE = 512

    fun getConcurrencyLevel(builderThreads: Int): Int {
      var result = 1
      var next = 1
      while (next <= builderThreads) {
        result = next
        next *= 2
      }
      return result
    }
  }
}

private class PersistentMVStoreSimpleMaplet<K, V>(
  store: MVStore,
  mapName: String,
  keyType: DataType<K>,
  valueType: DataType<V>,
) : Maplet<K, V> {
  private val map = store.openMap(mapName, MVMap.Builder<K, V>().keyType(keyType).valueType(valueType))

  override fun containsKey(key: K): Boolean = map.containsKey(key)

  override fun get(key: K): V? = map[key]

  override fun put(key: K, value: V?) {
    if (value == null) {
      map.remove(key)
    }
    else {
      map[key] = value
    }
  }

  override fun remove(key: K) {
    map.remove(key)
  }

  override fun getKeys(): Iterable<K> = map.keys

  override fun close() = Unit

  override fun flush() = Unit
}

private class PersistentMVStoreSetMultiMaplet<K, V>(
  store: MVStore,
  mapName: String,
  keyType: DataType<K>,
  valueType: DataType<V>,
) : MultiMaplet<K, V> {
  private val map = store.openMap(mapName, MVMap.Builder<K, MutableSet<V>>().keyType(keyType).valueType(SetDataType(valueType)))

  override fun containsKey(key: K): Boolean = map.containsKey(key)

  override fun get(key: K): Iterable<V> = map[key] ?: emptySet()

  override fun put(key: K, values: Iterable<V>) {
    val data = LinkedHashSet<V>().apply { values.forEach(::add) }
    if (data.isEmpty()) {
      map.remove(key)
    }
    else {
      map[key] = data
    }
  }

  override fun appendValue(key: K, value: V) {
    val updated = LinkedHashSet(map[key] ?: emptySet())
    if (updated.add(value)) {
      map[key] = updated
    }
  }

  override fun removeValue(key: K, value: V) {
    val current = map[key] ?: return
    val updated = LinkedHashSet(current)
    if (updated.remove(value)) {
      if (updated.isEmpty()) {
        map.remove(key)
      }
      else {
        map[key] = updated
      }
    }
  }

  override fun remove(key: K) {
    map.remove(key)
  }

  override fun getKeys(): Iterable<K> = map.keys

  override fun close() = Unit

  override fun flush() = Unit

  private class SetDataType<V>(private val valueType: DataType<V>) : BasicDataType<MutableSet<V>>() {
    override fun isMemoryEstimationAllowed(): Boolean = false

    override fun getMemory(obj: MutableSet<V>): Int = 0

    override fun write(buff: WriteBuffer, values: MutableSet<V>) {
      buff.putInt(values.size)
      values.forEach { valueType.write(buff, it) }
    }

    override fun read(buff: ByteBuffer): MutableSet<V> {
      val result = LinkedHashSet<V>()
      repeat(buff.int) {
        result.add(valueType.read(buff))
      }
      return result
    }

    override fun compare(a: MutableSet<V>, b: MutableSet<V>): Int = 0

    @Suppress("UNCHECKED_CAST")
    override fun createStorage(size: Int): Array<MutableSet<V>> = arrayOfNulls<MutableSet<V>?>(size) as Array<MutableSet<V>>
  }
}

private class ByteBufferDataInput(private val buffer: ByteBuffer) : DataInput {
  override fun readFully(b: ByteArray) {
    try {
      buffer.get(b, 0, b.size)
    }
    catch (e: BufferUnderflowException) {
      throw EOFException(e.message)
    }
  }

  override fun readFully(b: ByteArray, off: Int, len: Int) {
    try {
      buffer.get(b, off, len)
    }
    catch (e: BufferUnderflowException) {
      throw EOFException(e.message)
    }
  }

  override fun skipBytes(n: Int): Int {
    val skip = min(n, buffer.remaining())
    buffer.position(buffer.position() + skip)
    return skip
  }

  override fun readBoolean(): Boolean = buffer.get().toInt() != 0

  override fun readByte(): Byte = buffer.get()

  override fun readUnsignedByte(): Int = readByte().toInt() and 0xFF

  override fun readShort(): Short = buffer.short

  override fun readUnsignedShort(): Int = readShort().toInt() and 0xFF

  override fun readChar(): Char = buffer.char

  override fun readInt(): Int = buffer.int

  override fun readLong(): Long = buffer.long

  override fun readFloat(): Float = buffer.float

  override fun readDouble(): Double = buffer.double

  override fun readLine(): String = throw UnsupportedOperationException()

  override fun readUTF(): String = DataUtils.readString(buffer)
}

private class WriteBufferDataOutput(private val buffer: WriteBuffer) : DataOutput {
  override fun write(b: Int) {
    buffer.putInt(b)
  }

  override fun write(b: ByteArray) {
    buffer.put(b)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    buffer.put(b, off, len)
  }

  override fun writeBoolean(v: Boolean) {
    writeByte(if (v) 1 else 0)
  }

  override fun writeByte(v: Int) {
    buffer.put((v and 0xFF).toByte())
  }

  override fun writeShort(v: Int) {
    buffer.putShort((v and 0xFFFF).toShort())
  }

  override fun writeChar(v: Int) {
    buffer.putChar((v and 0xFFFF).toChar())
  }

  override fun writeInt(v: Int) {
    buffer.putInt(v)
  }

  override fun writeLong(v: Long) {
    buffer.putLong(v)
  }

  override fun writeFloat(v: Float) {
    buffer.putFloat(v)
  }

  override fun writeDouble(v: Double) {
    buffer.putDouble(v)
  }

  override fun writeBytes(s: String) {
    s.forEach { writeByte(it.code) }
  }

  override fun writeChars(s: String) {
    s.forEach { buffer.putChar(it) }
  }

  override fun writeUTF(s: String) {
    buffer.putVarInt(s.length).putStringData(s, s.length)
  }
}
