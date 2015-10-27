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

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.ArrayUtil
import com.intellij.util.SystemProperties
import gnu.trove.THashMap
import org.iq80.snappy.SnappyInputStream
import org.iq80.snappy.SnappyOutputStream
import org.jdom.Element
import org.jdom.output.Format
import java.io.ByteArrayInputStream
import java.io.OutputStreamWriter
import java.util.*
import java.util.concurrent.atomic.AtomicReferenceArray

class StateMap private constructor(private val names: Array<String>, private val states: AtomicReferenceArray<Any?>) {
  override fun toString(): String =
    if (this == EMPTY) "EMPTY" else states.toString();

  companion object {
    private val XML_FORMAT = Format.getRawFormat().setTextMode(Format.TextMode.TRIM).setOmitEncoding(true).setOmitDeclaration(true)

    val EMPTY = StateMap(emptyArray(), AtomicReferenceArray(0))

    public fun fromMap(map: Map<String, Any>): StateMap {
      if (map.isEmpty()) {
        return EMPTY
      }

      val names = map.keySet().toTypedArray()
      if (map !is TreeMap) {
        Arrays.sort(names)
      }

      val states = AtomicReferenceArray<Any?>(names.size())
      for (i in names.indices) {
        states.set(i, map.get(names[i]))
      }
      return StateMap(names, states)
    }

    public fun stateToElement(key: String, state: Any?, newLiveStates: Map<String, Element>? = null): Element {
      if (state is Element) {
        return state.clone()
      }
      else {
        return newLiveStates?.get(key) ?: unarchiveState(state as ByteArray)
      }
    }

    public fun getNewByteIfDiffers(key: String, newState: Any, oldState: ByteArray): ByteArray? {
      val newBytes = if (newState is Element) archiveState(newState) else newState as ByteArray
      if (Arrays.equals(newBytes, oldState)) {
        return null
      }
      else if (SystemProperties.getBooleanProperty("idea.log.changed.components", false)) {
        fun stateToString(state: Any) = JDOMUtil.writeParent(state as? Element ?: unarchiveState(state as ByteArray), "\n")

        val before = stateToString(oldState)
        val after = stateToString(newState)
        if (before == after) {
          LOG.info("Serialization error: serialized are different, but unserialized are equal")
        }
        else {
          LOG.info("$key ${StringUtil.repeat("=", 80 - key.length)}\nBefore:\n$before\nAfter:\n$after")
        }
      }
      return newBytes
    }

    private fun archiveState(state: Element): ByteArray {
      val byteOut = BufferExposingByteArrayOutputStream()
      OutputStreamWriter(SnappyOutputStream(byteOut), CharsetToolkit.UTF8_CHARSET).use {
        val xmlOutputter = JDOMUtil.MyXMLOutputter()
        xmlOutputter.format = XML_FORMAT
        xmlOutputter.output(state, it)
      }
      return ArrayUtil.realloc(byteOut.internalBuffer, byteOut.size())
    }

    private fun unarchiveState(state: ByteArray) = JDOMUtil.load(SnappyInputStream(ByteArrayInputStream(state)))
  }

  public fun toMutableMap(): MutableMap<String, Any> {
    val map = THashMap<String, Any>(names.size())
    for (i in names.indices) {
      map.put(names[i], states.get(i))
    }
    return map
  }

  /**
   * Sorted by name.
   */
  fun keys() = names

  public fun get(key: String): Any? {
    val index = Arrays.binarySearch(names, key)
    return if (index < 0) null else states.get(index)
  }

  fun getElement(key: String, newLiveStates: Map<String, Element>? = null) = stateToElement(key, get(key), newLiveStates)

  fun isEmpty() = names.isEmpty()

  fun hasState(key: String) = get(key) is Element

  public fun hasStates(): Boolean {
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

  public fun compare(key: String, newStates: StateMap, diffs: MutableSet<String>) {
    val oldState = get(key)
    val newState = newStates.get(key)
    if (oldState is Element) {
      if (!JDOMUtil.areElementsEqual(oldState as Element?, newState as Element?)) {
        diffs.add(key)
      }
    }
    else if (getNewByteIfDiffers(key, newState!!, oldState as ByteArray) != null) {
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
    return if (states.compareAndSet(index, state, archiveState(state))) state else getState(key, true)
  }

  public fun archive(key: String, state: Element?) {
    val index = Arrays.binarySearch(names, key)
    if (index < 0) {
      return
    }

    val currentState = states.get(index)
    LOG.assertTrue(currentState is Element)
    states.set(index, if (state == null) null else archiveState(state))
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
    newBytes = StateMap.getNewByteIfDiffers(key, newState, oldState as ByteArray)
    if (newBytes == null) {
      return null
    }
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
    newBytes = StateMap.getNewByteIfDiffers(key, newState, oldState as ByteArray) ?: return false
  }

  states.put(key, newBytes ?: newState)
  return true
}