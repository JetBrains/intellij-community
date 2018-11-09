/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jdom.Element
import java.util.function.Consumer
import java.util.function.Function

// Empty unknown tags supported to simplify client write code (with and without unknown elements)
class UnknownElementWriter internal constructor(private val unknownElements: Map<String, Element> = emptyMap()) {
  companion object {
    @JvmField
    val EMPTY: UnknownElementWriter = UnknownElementWriter()
  }

  fun <T> write(outElement: Element, items: Collection<T>, itemToTagName: Function<T, String>, writer: Consumer<T>) {
    val knownNameToWriter = THashMap<String, T>(items.size)
    for (item in items) {
      knownNameToWriter.put(itemToTagName.apply(item), item)
    }
    write(outElement, knownNameToWriter, writer)
  }

  fun <T> write(outElement: Element, knownNameToWriter: Map<String, T>, writer: Consumer<T>) {
    val names: Set<String>
    if (unknownElements.isEmpty()) {
      names = knownNameToWriter.keys
    }
    else {
      names = THashSet<String>(unknownElements.keys)
      names.addAll(knownNameToWriter.keys)
    }

    val sortedNames = names.toTypedArray()
    sortedNames.sort()
    for (name in sortedNames) {
      val known = knownNameToWriter.get(name)
      if (known == null) {
        outElement.addContent(unknownElements.get(name)!!.clone())
      }
      else {
        writer.accept(known)
      }
    }
  }
}

class UnknownElementCollector {
  private val knownTagNames = THashSet<String>()

  fun addKnownName(name: String) {
    knownTagNames.add(name)
  }

  fun createWriter(element: Element): UnknownElementWriter? {
    var unknownElements: MutableMap<String, Element>? = null
    val iterator = element.children.iterator()
    for (child in iterator) {
      if (child.name != "option" && !knownTagNames.contains(child.name)) {
        if (unknownElements == null) {
          unknownElements = THashMap()
        }
        unknownElements.put(child.name, child)
        iterator.remove()
      }
    }

    return unknownElements?.let(::UnknownElementWriter) ?: UnknownElementWriter.EMPTY
  }
}