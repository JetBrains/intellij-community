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

import com.intellij.openapi.diagnostic.Logger
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
import org.jdom.JDOMException
import org.jdom.output.Format
import org.jdom.output.XMLOutputter

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.Arrays
import java.util.concurrent.atomic.AtomicReferenceArray

class StateMap private constructor(private val names: Array<String>, private val states: AtomicReferenceArray<Any>) {
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
  fun keys(): Array<String> {
    return names
  }

  public fun get(key: String): Any? {
    val index = Arrays.binarySearch(names, key)
    return if (index < 0) null else states.get(index)
  }

  throws(IOException::class)
  public fun getElement(key: String, newLiveStates: Map<String, Element>): Element {
    return stateToElement(key, get(key), newLiveStates)
  }

  public fun isEmpty(): Boolean {
    return names.size() == 0
  }

  public fun getState(key: String): Element? {
    val state = get(key)
    return if (state is Element) state else null
  }

  public fun hasState(key: String): Boolean {
    return get(key) is Element
  }

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

  public fun getStateAndArchive(key: String): Element? {
    val index = Arrays.binarySearch(names, key)
    if (index < 0) {
      return null
    }

    val state = states.get(index)
    if (state !is Element) {
      return null
    }

    if (states.compareAndSet(index, state, archiveState(state))) {
      return state
    }
    else {
      return getStateAndArchive(key)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(javaClass<StateMap>())

    private val XML_FORMAT = Format.getRawFormat().setTextMode(Format.TextMode.TRIM).setOmitEncoding(true).setOmitDeclaration(true)

    val EMPTY = StateMap(emptyArray(), AtomicReferenceArray(0))

    public fun fromMap(map: Map<String, Any>): StateMap {
      if (map.isEmpty()) {
        return EMPTY
      }

      val names = map.keySet().toTypedArray()
      Arrays.sort(names)
      val states = AtomicReferenceArray<Any>(names.size())
      var i = 0
      val n = names.size()
      while (i < n) {
        states.set(i, map.get(names[i]))
        i++
      }
      return StateMap(names, states)
    }

    public fun stateToElement(key: String, state: Any?, newLiveStates: Map<String, Element> = emptyMap<String, Element>()): Element {
      if (state is Element) {
        return state.clone()
      }
      else {
        return newLiveStates.get(key) ?: unarchiveState(state as ByteArray)
      }
    }

    public fun getNewByteIfDiffers(key: String, newState: Any, oldState: ByteArray): ByteArray? {
      val newBytes = if (newState is Element) archiveState(newState) else newState as ByteArray
      if (Arrays.equals(newBytes, oldState)) {
        return null
      }
      else if (LOG.isDebugEnabled() && SystemProperties.getBooleanProperty("idea.log.changed.components", false)) {
        val before = stateToString(oldState)
        val after = stateToString(newState)
        if (before == after) {
          LOG.debug("Serialization error: serialized are different, but unserialized are equal")
        }
        else {
          LOG.debug(key + " " + StringUtil.repeat("=", 80 - key.length()) + "\nBefore:\n" + before + "\nAfter:\n" + after)
        }
      }
      return newBytes
    }

    private fun archiveState(state: Element): ByteArray {
      val byteOut = BufferExposingByteArrayOutputStream()
      try {
        val writer = OutputStreamWriter(SnappyOutputStream(byteOut), CharsetToolkit.UTF8_CHARSET)
        try {
          val xmlOutputter = JDOMUtil.MyXMLOutputter()
          xmlOutputter.setFormat(XML_FORMAT)
          xmlOutputter.output(state, writer)
        }
        finally {
          writer.close()
        }
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }

      return ArrayUtil.realloc(byteOut.getInternalBuffer(), byteOut.size())
    }

    private fun unarchiveState(state: ByteArray) = JDOMUtil.load(SnappyInputStream(ByteArrayInputStream(state)))

    fun stateToString(state: Any): String {
      val element: Element
      if (state is Element) {
        element = state
      }
      else {
        try {
          element = unarchiveState(state as ByteArray)
        }
        catch (e: Throwable) {
          LOG.error(e)
          return "internal error"
        }
      }
      return JDOMUtil.writeParent(element, "\n")
    }
  }
}