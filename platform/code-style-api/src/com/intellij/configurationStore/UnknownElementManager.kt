// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import org.jdom.Element
import java.util.function.Consumer
import java.util.function.Function

// Empty unknown tags supported to simplify client write code (with and without unknown elements)
internal class UnknownElementWriter internal constructor(private val unknownElements: Map<String, Element> = emptyMap()) {
  companion object {
    @JvmField
    val EMPTY: UnknownElementWriter = UnknownElementWriter()
  }

  fun <T> write(outElement: Element, items: Collection<T>, itemToTagName: Function<T, String>, writer: Consumer<T>) {
    val knownNameToWriter = HashMap<String, T>(items.size)
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
      names = HashSet<String>(unknownElements.keys)
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

internal class UnknownElementCollector {
  private val knownTagNames = HashSet<String>()

  fun addKnownName(name: String) {
    knownTagNames.add(name)
  }

  fun createWriter(element: Element): UnknownElementWriter {
    var unknownElements: MutableMap<String, Element>? = null
    val iterator = element.children.iterator()
    for (child in iterator) {
      if (child.name != "option" && !knownTagNames.contains(child.name)) {
        if (unknownElements == null) {
          unknownElements = HashMap()
        }
        unknownElements.put(child.name, child)
        iterator.remove()
      }
    }

    return unknownElements?.let(::UnknownElementWriter) ?: UnknownElementWriter.EMPTY
  }
}