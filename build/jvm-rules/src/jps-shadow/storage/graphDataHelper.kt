@file:Suppress("SSBasedInspection")

package org.jetbrains.jps.dependency.storage

import androidx.collection.ScatterSet
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.collections.immutable.PersistentSet
import org.h2.mvstore.MVMap
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.FactoredExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.MultiMaplet

interface MultiMapletEx<K : Any, V : Any> : MultiMaplet<K, V> {
  fun put(key: K, values: ScatterSet<V>)

  fun removeValues(key: K, values: ScatterSet<V>)
}

interface MvStoreContainerFactory {
  fun <K : Any, V : Any> openMap(mapName: String, mapBuilder: MVMap.Builder<K, PersistentSet<V>>): MultiMapletEx<K, V>

  fun <K : Any, V : Any> openInMemoryMap(): MultiMapletEx<K, V>

  fun getStringEnumerator(): StringEnumerator

  fun getElementInterner(): (ExternalizableGraphElement) -> ExternalizableGraphElement
}

internal fun <T : ExternalizableGraphElement> doWriteGraphElement(output: GraphDataOutput, element: T) {
  ClassRegistry.writeClassId(element.javaClass, output)
  if (element is FactoredExternalizableGraphElement<*>) {
    output.writeGraphElement(element.getFactorData())
  }
  element.write(output)
}

internal fun <T : ExternalizableGraphElement> doWriteGraphElementCollection(
  output: GraphDataOutput,
  elementType: Class<out T>,
  collection: Iterable<T>,
) {
  val classInfo = ClassRegistry.writeClassId(elementType, output)
  if (classInfo.isFactored) {
    val elementGroups = Object2ObjectOpenHashMap<ExternalizableGraphElement, MutableList<FactoredExternalizableGraphElement<*>>>()
    for (e in collection) {
      e as FactoredExternalizableGraphElement<*>
      elementGroups.computeIfAbsent(e.getFactorData()) { ArrayList() }.add(e)
    }
    output.writeInt(elementGroups.size)
    for ((key, list) in elementGroups.object2ObjectEntrySet().fastIterator()) {
      output.writeGraphElement(key)
      output.writeInt(list.size)
      for (t in list) {
        t.write(output)
      }
    }
  }
  else {
    collection as Collection<*>
    output.writeInt(collection.size)
    for (t in collection) {
      t.write(output)
    }
  }
}