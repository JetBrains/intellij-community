// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.util

import com.intellij.openapi.fileEditor.impl.text.VersionedExternalizer
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import it.unimi.dsi.fastutil.bytes.ByteArrayList
import java.io.DataInput
import java.io.DataOutput

/**
 * Stores up to 127 elements with a single byte payload and reference data
 */
class TinyTree<T> private constructor(
  private val firstChild: ByteArrayList,
  private val nextChild: ByteArrayList,
  private val payload: ByteArrayList,
  private val data: ArrayList<T>,
) {
  companion object {
    private const val NO_ELEMENT: Byte = -1
  }

  constructor(rootPayload: Byte, rootData: T) : this(ByteArrayList(), ByteArrayList(), ByteArrayList(), ArrayList()) {
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

  val size: Int
    get() = payload.size

  class TooManyElementsException : Exception()

  abstract class Externalizer<T> : VersionedExternalizer<TinyTree<T>> {

    companion object {
      // increment on format changed
      private const val SERDE_VERSION = 0
    }

    override fun serdeVersion(): Int = SERDE_VERSION

    abstract fun writeDataPayload(output: DataOutput, payload: T)

    abstract fun readDataPayload(input: DataInput): T

    override fun save(output: DataOutput, tree: TinyTree<T>) {
      writeINT(output, tree.size)
      writeByteArray(output, tree.firstChild)
      writeByteArray(output, tree.nextChild)
      writeByteArray(output, tree.payload)
      for (dataPayload in tree.data) {
        writeDataPayload(output, dataPayload)
      }
    }

    override fun read(input: DataInput): TinyTree<T> {
      val size       = readINT(input)
      val firstChild = readByteArray(input, size)
      val nextChild  = readByteArray(input, size)
      val payload    = readByteArray(input, size)
      val data       = ArrayList<T>(size)
      repeat(size) {
        data.add(readDataPayload(input))
      }
      return TinyTree(firstChild, nextChild, payload, data)
    }

    private fun writeByteArray(output: DataOutput, byteArray: ByteArrayList) {
      output.write(byteArray.elements(), 0, byteArray.size)
    }

    private fun readByteArray(input: DataInput, size: Int): ByteArrayList {
      val bytes = ByteArray(size).also { input.readFully(it) }
      return ByteArrayList(bytes)
    }
  }
}