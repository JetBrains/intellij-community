// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package org.jetbrains.jps.dependency

import androidx.collection.MutableObjectList
import androidx.collection.MutableScatterMap
import androidx.collection.ObjectList
import androidx.collection.objectListOf
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import org.jetbrains.jps.dependency.storage.ClassRegistry
import java.io.DataOutput

interface GraphDataOutput : DataOutput {
  fun <T : ExternalizableGraphElement> writeGraphElement(element: T) {
    ClassRegistry.writeClassId(element.javaClass, this)
    if (element is FactoredExternalizableGraphElement<*>) {
      writeGraphElement(element.getFactorData())
    }
    element.write(this)
  }

  fun writeRawLong(v: Long)

  override fun writeBytes(s: String) = throw UnsupportedOperationException("do not use")

  override fun writeChars(s: String) = throw UnsupportedOperationException("do not use")

  override fun write(v: Int) {
    writeInt(v)
  }

  fun writeUsages(usages: Collection<Usage>) {
    val totalSize = usages.size
    when (totalSize) {
      0 -> {
        writeInt(0)
      }
      1 -> {
        writeInt(1)
        val usage = usages.single()
        doWriteGraphElementCollection(this@GraphDataOutput, usage.javaClass, objectListOf(usage))
      }
      else -> {
        val classToItem = MutableScatterMap<Class<Usage>, MutableObjectList<Usage>>()
        for (usage in usages) {
          classToItem.compute(usage.javaClass) { k, v -> (v ?: MutableObjectList()).also { it.add(usage) } }
        }

        writeInt(classToItem.size)
        classToItem.forEach { k, v ->
          doWriteGraphElementCollection(this@GraphDataOutput, k, v)
        }
      }
    }
  }
}

internal inline fun <T : Any> GraphDataOutput.writeCollection(collection: Collection<T>, writer: GraphDataOutput.(T) -> Unit) {
  writeInt(collection.size)
  for (t in collection) {
    writer(t)
  }
}

private fun <T : ExternalizableGraphElement> doWriteGraphElementCollection(
  output: GraphDataOutput,
  elementType: Class<out T>,
  collection: ObjectList<T>,
) {
  val classInfo = ClassRegistry.writeClassId(elementType, output)
  if (classInfo.isFactored) {
    val elementGroups = Object2ObjectLinkedOpenHashMap<ExternalizableGraphElement, MutableObjectList<FactoredExternalizableGraphElement<*>>>()
    collection.forEach { e ->
      e as FactoredExternalizableGraphElement<*>
      elementGroups.computeIfAbsent(e.getFactorData()) { MutableObjectList() }.add(e)
    }
    output.writeInt(elementGroups.size)
    elementGroups.forEach { key, list ->
      output.writeGraphElement(key)
      output.writeInt(list.size)
      list.forEach {
        it.write(output)
      }
    }
  }
  else {
    output.writeInt(collection.size)
    collection.forEach {
      it.write(output)
    }
  }
}