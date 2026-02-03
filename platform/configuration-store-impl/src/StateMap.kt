// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SystemProperties
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.util.*
import java.util.concurrent.atomic.AtomicReferenceArray

@ApiStatus.Internal
class StateMap private constructor(private val names: Array<String>, private val states: AtomicReferenceArray<Any>) {
  override fun toString(): String = if (this === EMPTY) "EMPTY" else states.toString()

  companion object {
    @JvmField
    internal val EMPTY: StateMap = StateMap(names = ArrayUtilRt.EMPTY_STRING_ARRAY, states = AtomicReferenceArray(0))

    fun fromMap(map: Map<String, Any>): StateMap {
      if (map.isEmpty()) {
        return EMPTY
      }

      val names = map.keys.toTypedArray()
      if (map !is TreeMap) {
        Arrays.sort(names)
      }

      val states = AtomicReferenceArray<Any>(names.size)
      for (i in names.indices) {
        states.set(i, map.get(names[i]))
      }
      return StateMap(names, states)
    }
  }

  fun toMutableMap(): MutableMap<String, Any> {
    val map = HashMap<String, Any>(names.size)
    for (i in names.indices) {
      map.put(names[i], states.get(i))
    }
    return map
  }

  /**
   * Sorted by name.
   */
  fun keys(): Array<String> = names

  fun get(key: String): Any? {
    val index = Arrays.binarySearch(names, key)
    return if (index < 0) null else states.get(index)
  }

  fun getElement(key: String, newLiveStates: Map<String, Element>? = null): Element? {
    val state = get(key)
    return if (state is Element) state.clone() else newLiveStates?.get(key) ?: (state as? ByteArray)?.let(::unarchiveState)
  }

  fun isEmpty(): Boolean = names.isEmpty()

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
    val index = names.binarySearch(key)
    if (index < 0) {
      return null
    }

    val prev = if (archive) {
      states.getAndUpdate(index) { state -> if (state is Element) archiveState(state).toByteArray() else state }
    }
    else {
      states.updateAndGet(index) { state -> if (state is ByteArray) unarchiveState(state) else state }
    }
    return prev as? Element
  }

  fun archive(key: String, state: Element?) {
    val index = Arrays.binarySearch(names, key)
    if (index >= 0) {
      states.set(index, state?.let { archiveState(state).toByteArray() })
    }
  }
}

internal fun setStateAndCloneIfNeeded(key: String, newState: Element?, oldStates: StateMap, newLiveStates: MutableMap<String, Element>?): MutableMap<String, Any>? {
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
  newStates.put(key, newBytes ?: JDOMUtil.internElement(newState))
  return newStates
}

// true if updated (not equals to previous state)
internal fun updateState(states: MutableMap<String, Any>, key: String, newState: Element?, newLiveStates: MutableMap<String, Element>?): Boolean {
  if (newState == null || newState.isEmpty) {
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

  states.put(key, newBytes ?: JDOMUtil.internElement(newState))
  return true
}

private fun archiveState(state: Element): BufferExposingByteArrayOutputStream {
  val byteOut = BufferExposingByteArrayOutputStream()
  byteOut.use { serializeElementToBinary(state, it) }
  return byteOut
}

private fun unarchiveState(state: ByteArray): Element {
  return ByteArrayInputStream(state).use { deserializeElementFromBinary(it) }
}

private fun getNewByteIfDiffers(key: String, newState: Any, oldState: ByteArray): ByteArray? {
  val newBytes: ByteArray
  if (newState is Element) {
    val byteOut = archiveState(newState)
    val newSize = byteOut.size()
    if (oldState.size == newSize && Arrays.equals(byteOut.internalBuffer, 0, newSize, oldState, 0, oldState.size)) {
      return null
    }
    newBytes = byteOut.toByteArray()
  }
  else {
    newBytes = newState as ByteArray
    if (newBytes.contentEquals(oldState)) {
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
      LOG.info("$key ${"=".repeat(80 - key.length)}\nBefore:\n$before\nAfter:\n$after")
    }
  }
  return newBytes
}