/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.SystemProperties
import gnu.trove.THashMap
import org.iq80.snappy.SnappyFramedInputStream
import org.iq80.snappy.SnappyFramedOutputStream
import org.jdom.Element
import java.io.ByteArrayInputStream
import java.util.*
import java.util.concurrent.atomic.AtomicReferenceArray

fun archiveState(state: Element): BufferExposingByteArrayOutputStream {
  val byteOut = BufferExposingByteArrayOutputStream()
  SnappyFramedOutputStream(byteOut).use {
    serializeElementToBinary(state, it)
  }
  return byteOut
}

private fun unarchiveState(state: ByteArray) = SnappyFramedInputStream(ByteArrayInputStream(state), false).use { deserializeElementFromBinary(it) }

fun getNewByteIfDiffers(key: String, newState: Any, oldState: ByteArray): ByteArray? {
  val newBytes: ByteArray
  if (newState is Element) {
    val byteOut = archiveState(newState)
    if (arrayEquals(byteOut.internalBuffer, oldState, byteOut.size())) {
      return null
    }

    newBytes = ArrayUtil.realloc(byteOut.internalBuffer, byteOut.size())
  }
  else {
    newBytes = newState as ByteArray
    if (Arrays.equals(newBytes, oldState)) {
      return null
    }
  }

  val logChangedComponents = SystemProperties.getBooleanProperty("idea.log.changed.components", false)
  if (ApplicationManager.getApplication().isUnitTestMode || logChangedComponents ) {
    fun stateToString(state: Any) = JDOMUtil.write(state as? Element ?: unarchiveState(state as ByteArray), "\n")

    val before = stateToString(oldState)
    val after = stateToString(newState)
    if (before == after) {
      throw IllegalStateException("$key serialization error - serialized are different, but unserialized are equal")
    }
    else if (logChangedComponents) {
      LOG.info("$key ${StringUtil.repeat("=", 80 - key.length)}\nBefore:\n$before\nAfter:\n$after")
    }
  }
  return newBytes
}

fun stateToElement(key: String, state: Any?, newLiveStates: Map<String, Element>? = null): Element? {
  if (state is Element) {
    return state.clone()
  }
  else {
    return newLiveStates?.get(key) ?: (state as? ByteArray)?.let(::unarchiveState)
  }
}

class StateMap private constructor(private val names: Array<String>, private val states: AtomicReferenceArray<Any?>) {
  override fun toString() = if (this == EMPTY) "EMPTY" else states.toString()

  companion object {
    val EMPTY = StateMap(emptyArray(), AtomicReferenceArray(0))

    fun fromMap(map: Map<String, Any>): StateMap {
      if (map.isEmpty()) {
        return EMPTY
      }

      val names = map.keys.toTypedArray()
      if (map !is TreeMap) {
        Arrays.sort(names)
      }

      val states = AtomicReferenceArray<Any?>(names.size)
      for (i in names.indices) {
        states.set(i, map[names[i]])
      }
      return StateMap(names, states)
    }
  }

  fun toMutableMap(): MutableMap<String, Any> {
    val map = THashMap<String, Any>(names.size)
    for (i in names.indices) {
      map.put(names[i], states.get(i))
    }
    return map
  }

  /**
   * Sorted by name.
   */
  fun keys() = names

  fun get(key: String): Any? {
    val index = Arrays.binarySearch(names, key)
    return if (index < 0) null else states.get(index)
  }

  fun getElement(key: String, newLiveStates: Map<String, Element>? = null) = stateToElement(key, get(key), newLiveStates)

  fun isEmpty(): Boolean = names.isEmpty()

  fun hasState(key: String) = get(key) is Element

  fun hasStates(): Boolean {
    if (isEmpty()) {
      return false
    }

    for (i in names.indices) {
      if (states.get(i) is Element) {
        return true
      }
    }
    return false
  }

  fun compare(key: String, newStates: StateMap, diffs: MutableSet<String>) {
    val oldState = get(key)
    val newState = newStates.get(key)
    if (oldState is Element) {
      if (!JDOMUtil.areElementsEqual(oldState as Element?, newState as Element?)) {
        diffs.add(key)
      }
    }
    else if (oldState == null) {
      if (newState != null) {
        diffs.add(key)
      }
    }
    else if (newState == null || getNewByteIfDiffers(key, newState, oldState as ByteArray) != null) {
      diffs.add(key)
    }
  }

  fun getState(key: String, archive: Boolean = false): Element? {
    val index = Arrays.binarySearch(names, key)
    if (index < 0) {
      return null
    }

    val state = states.get(index) as? Element ?: return null
    if (!archive) {
      return state
    }
    return if (states.compareAndSet(index, state, archiveState(state).toByteArray())) state else getState(key, true)
  }

  fun archive(key: String, state: Element?) {
    val index = Arrays.binarySearch(names, key)
    if (index < 0) {
      return
    }

    states.set(index, state?.let { archiveState(state).toByteArray() })
  }
}

fun setStateAndCloneIfNeed(key: String, newState: Element?, oldStates: StateMap, newLiveStates: MutableMap<String, Element>? = null): MutableMap<String, Any>? {
  val oldState = oldStates.get(key)
  if (newState == null || JDOMUtil.isEmpty(newState)) {
    if (oldState == null) {
      return null
    }

    val newStates = oldStates.toMutableMap()
    newStates.remove(key)
    return newStates
  }

  newLiveStates?.put(key, newState)

  var newBytes: ByteArray? = null
  if (oldState is Element) {
    if (JDOMUtil.areElementsEqual(oldState as Element?, newState)) {
      return null
    }
  }
  else if (oldState != null) {
    newBytes = getNewByteIfDiffers(key, newState, oldState as ByteArray) ?: return null
  }

  val newStates = oldStates.toMutableMap()
  newStates.put(key, newBytes ?: newState)
  return newStates
}

// true if updated (not equals to previous state)
internal fun updateState(states: MutableMap<String, Any>, key: String, newState: Element?, newLiveStates: MutableMap<String, Element>? = null): Boolean {
  if (newState == null || JDOMUtil.isEmpty(newState)) {
    states.remove(key)
    return true
  }

  newLiveStates?.put(key, newState)

  val oldState = states.get(key)

  var newBytes: ByteArray? = null
  if (oldState is Element) {
    if (JDOMUtil.areElementsEqual(oldState as Element?, newState)) {
      return false
    }
  }
  else if (oldState != null) {
    newBytes = getNewByteIfDiffers(key, newState, oldState as ByteArray) ?: return false
  }

  states.put(key, newBytes ?: newState)
  return true
}

private fun arrayEquals(a: ByteArray, a2: ByteArray, size: Int = a.size): Boolean {
  if (a === a2) {
    return true
  }

  if (a2.size != size) {
    return false
  }

  for (i in 0 until size) {
    if (a[i] != a2[i]) {
      return false
    }
  }

  return true
}