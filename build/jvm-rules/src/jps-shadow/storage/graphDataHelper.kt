@file:Suppress("SSBasedInspection")

package org.jetbrains.jps.dependency.storage

import androidx.collection.ScatterSet
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