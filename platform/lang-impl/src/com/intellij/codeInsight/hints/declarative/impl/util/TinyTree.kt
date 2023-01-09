// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.util

import it.unimi.dsi.fastutil.bytes.ByteArrayList

/**
 * Stores up to 127 elements with a single byte payload and reference data
 */
class TinyTree<T>(rootPayload: Byte, rootData: T) {
  companion object {
    private const val NO_ELEMENT: Byte = -1
  }

  private val firstChild: ByteArrayList = ByteArrayList()
  private val nextChild: ByteArrayList = ByteArrayList()
  private val payload: ByteArrayList = ByteArrayList()
  private val data: ArrayList<T> = ArrayList()

  init {
    payload.add(rootPayload)
    data.add(rootData)
    firstChild.add(NO_ELEMENT)
    nextChild.add(NO_ELEMENT)
  }

  /**
   * Adds node to the tree, it will appear before other children
   * @return index
   */
  fun add(parent: Byte, nodePayload: Byte, data: T) : Byte {
    val index = payload.size.toByte()
    if (index < 0) {
      throw TooManyElementsException()
    }
    payload.add(nodePayload)
    this.data.add(data)
    firstChild.add(NO_ELEMENT)
    val previousFirstChild = firstChild.getByte(parent.toInt())
    nextChild.add(previousFirstChild)
    firstChild.set(parent.toInt(), index)
    return index
  }

  fun getBytePayload(index: Byte) : Byte {
    return payload.getByte(index.toInt())
  }

  fun getDataPayload(index: Byte) : T {
    return data[index.toInt()]
  }

  fun reverseChildren() {
    for (i in 0 until firstChild.size) {
      val firstChildIndex = firstChild.getByte(i)
      if (firstChildIndex == NO_ELEMENT) continue

      // reversing single linked list of children nodes
      var prevIndex = NO_ELEMENT
      var currIndex = firstChildIndex
      var nextIndex: Byte
      while (currIndex != NO_ELEMENT) {
        nextIndex = nextChild.getByte(currIndex.toInt())
        nextChild.set(currIndex.toInt(), prevIndex)
        prevIndex = currIndex
        currIndex = nextIndex
      }
      firstChild.set(i, prevIndex)
    }
  }
  
  fun setBytePayload(nodePayload: Byte, index: Byte) {
    payload.set(index.toInt(), nodePayload)
  }

  fun processChildren(index: Byte, f: (index: Byte) -> Boolean) {
    var currentChildIndex = firstChild.getByte(index.toInt())
    while (currentChildIndex != NO_ELEMENT) {
      if (!f(currentChildIndex)) {
        break
      }
      currentChildIndex = nextChild.getByte(currentChildIndex.toInt())
    }
  }

  /**
   * Processes sync children 2 trees: this and [other]. If the number of children is different, it will pass minimum of both
   */
  fun syncProcessChildren(myIndex: Byte, otherIndex: Byte, other: TinyTree<T>, f: (myIndex: Byte, otherIndex: Byte) -> Boolean) {
    var currentThisChildIndex = firstChild.getByte(myIndex.toInt())
    var currentOtherChildIndex = other.firstChild.getByte(otherIndex.toInt())
    while (currentThisChildIndex != NO_ELEMENT && currentOtherChildIndex != NO_ELEMENT) {
      if (!f(currentThisChildIndex, currentOtherChildIndex)) {
        break
      }
      currentThisChildIndex = nextChild.getByte(currentThisChildIndex.toInt())
      currentOtherChildIndex = other.nextChild.getByte(currentOtherChildIndex.toInt())
    }
  }

  class TooManyElementsException : Exception()
}