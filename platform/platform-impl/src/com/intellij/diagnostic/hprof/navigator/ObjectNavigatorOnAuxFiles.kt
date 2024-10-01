/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.navigator

import com.intellij.diagnostic.hprof.classstore.ClassDefinition
import com.intellij.diagnostic.hprof.classstore.ClassStore
import com.intellij.diagnostic.hprof.parser.Type
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import kotlin.experimental.and

@ApiStatus.Internal
class ObjectNavigatorOnAuxFiles(
  private val roots: Long2ObjectMap<RootReason>,
  private val auxOffsets: ByteBuffer,
  private val aux: ByteBuffer,
  classStore: ClassStore,
  instanceCount: Long,
  private val idSize: Int
) : ObjectNavigator(classStore, instanceCount) {
  override fun getClass(): ClassDefinition = currentClass!!

  override fun getClassForObjectId(id: Long): ClassDefinition {
    auxOffsets.position((id * 4).toInt())
    aux.position(auxOffsets.int)
    val classId = readId(aux)
    return if (classId == 0) classStore.classClass else classStore[classId]
  }

  private var softWeakReferenceIndex: Int = -1
  private var currentObjectId = 0L
  private var arraySize = 0
  private var arrayData: ByteArray? = null
  private var currentClass: ClassDefinition? = null
  private val references = LongArrayList()
  private var softWeakReferenceId = 0L

  private enum class ReferenceType { Strong, Weak, Soft }

  private var referenceType = ReferenceType.Strong
  private var extraData = 0

  override val id: Long
    get() = currentObjectId

  override fun createRootsIterator(): Iterator<RootObject> {
    return object : Iterator<RootObject> {
      val internalIterator = roots.keys.iterator()
      override fun hasNext(): Boolean {
        return internalIterator.hasNext()
      }

      override fun next(): RootObject {
        val key = internalIterator.nextLong()
        return RootObject(key, roots.get(key))
      }
    }
  }

  override fun getReferencesCopy(): LongArrayList = LongArrayList(references)

  override fun isNull(): Boolean {
    return id == 0L
  }

  override fun goTo(id: Long, referenceResolution: ReferenceResolution) {
    auxOffsets.position((id * 4).toInt())
    aux.position(auxOffsets.int)
    currentObjectId = id
    references.clear()
    softWeakReferenceId = 0L
    softWeakReferenceIndex = -1
    referenceType = ReferenceType.Strong
    extraData = 0

    if (id == 0L) {
      currentClass = null
      return
    }
    val classId = readId(aux)
    val classDefinition: ClassDefinition
    if (classId == 0) {
      classDefinition = classStore.classClass
    }
    else {
      classDefinition = classStore[classId]
    }
    currentClass = classDefinition
    if (classId == 0) {
      preloadClass(id.toInt(), referenceResolution)
      return
    }
    if (classDefinition.isPrimitiveArray()) {
      preloadPrimitiveArray()
      return
    }
    if (classDefinition.isArray()) {
      preloadObjectArray(referenceResolution)
      return
    }
    preloadInstance(classDefinition, referenceResolution)
  }

  override fun getStringInstanceFieldValue(): String? {
    val coder = extraData
    goToInstanceField("java.lang.String", "value")
    arrayData?.let { data ->
      val arrayClass = getClass()
      if (arrayClass.name == "[B") {  // Java 9+
        if (coder == 0 /* String.LATIN1 */) {
          return data.toString(Charsets.ISO_8859_1)
        }
        else if (coder == 1 /* String.UTF16 */) {
          return decodeUTF16String(data)
        }
      }
      else if (arrayClass.name == "[C") {  // Java 8 and earlier
        val buffer = ByteBuffer.wrap(data).asCharBuffer()
        return buffer.toString()
      }
    }
    return null
  }

  private fun decodeUTF16String(data: ByteArray): String {
    val utf16Class = classStore.getClassIfExists("java.lang.StringUTF16")
    if (utf16Class != null) {
      val hiByteShift = utf16Class.getPrimitiveStaticFieldValue("HI_BYTE_SHIFT")
      val loByteShift = utf16Class.getPrimitiveStaticFieldValue("LO_BYTE_SHIFT")
      if (hiByteShift != null && loByteShift != null) {
        val chars = CharArray(data.size / 2) { index ->
          ((data[index * 2].toInt() shl hiByteShift.toInt()) or (data [index * 2 + 1].toInt() shl loByteShift.toInt())).toChar()
        }
        return String(chars)
      }
    }
    return data.toString(Charsets.UTF_16)
  }

  override fun getExtraData(): Int {
    return extraData
  }

  private fun preloadPrimitiveArray() {
    arraySize = readNonNegativeLEB128Int(aux)
    val size = Type.getType(getClass().name).size
    arrayData = ByteArray(arraySize * size)
    aux.get(arrayData)
  }

  private fun preloadClass(classId: Int,
                           referenceResolution: ReferenceResolution) {
    arraySize = 0
    arrayData = null

    if (referenceResolution != ReferenceResolution.NO_REFERENCES) {
      val classDefinition = classStore[classId]
      classDefinition.constantFields.forEach { l -> references.add(l) }
      classDefinition.objectStaticFields.forEach { references.add(it.value) }
      references.add(classDefinition.classLoaderId)
    }
  }

  private fun preloadObjectArray(referenceResolution: ReferenceResolution) {
    val nullElementsCount = readNonNegativeLEB128Int(aux)
    val nonNullElementsCount = readNonNegativeLEB128Int(aux)

    arraySize = nullElementsCount + nonNullElementsCount
    arrayData = null

    if (referenceResolution != ReferenceResolution.NO_REFERENCES) {
      for (i in 0 until nonNullElementsCount) {
        references.add(readId(aux).toLong())
      }
    }
  }

  private fun preloadInstance(classDefinition: ClassDefinition,
                              referenceResolution: ReferenceResolution) {
    arraySize = 0
    arrayData = null

    if (referenceResolution == ReferenceResolution.NO_REFERENCES) {
      return
    }

    var c = classDefinition
    var isSoftReference = false
    var isWeakReference = false
    val includeSoftWeakReferences = referenceResolution == ReferenceResolution.ALL_REFERENCES
    do {
      isSoftReference = isSoftReference || classStore.softReferenceClass == c
      isWeakReference = isWeakReference || classStore.weakReferenceClass == c
      val fields = c.refInstanceFields
      fields.forEach {
        val reference = readId(aux)
        if (!(isSoftReference || isWeakReference) || it.name != "referent") {
          references.add(reference.toLong())
        }
        else {
          softWeakReferenceId = reference.toLong()
          softWeakReferenceIndex = references.size // current index in references list
          referenceType = if (isSoftReference) ReferenceType.Soft else ReferenceType.Weak
          // Soft/weak reference
          if (includeSoftWeakReferences) {
            references.add(reference.toLong())
          }
          else {
            references.add(0L)
          }
        }
      }
      val superClassId = c.superClassId
      if (superClassId == 0L) {
        break
      }
      c = classStore[superClassId]
    }
    while (true)

    references.add(classDefinition.id)

    if (classDefinition == directByteBufferClass) {
      extraData = readNonNegativeLEB128Int(aux)
    }
    else if (classDefinition == stringClass) {
      extraData = aux.get().toInt()
    }
  }

  private val directByteBufferClass = classStore.getClassIfExists("java.nio.DirectByteBuffer")
  private val stringClass = classStore.getClassIfExists("java.lang.String")

  override fun getSoftReferenceId(): Long {
    return if (referenceType == ReferenceType.Soft) softWeakReferenceId else 0
  }

  override fun getWeakReferenceId(): Long {
    return if (referenceType == ReferenceType.Weak) softWeakReferenceId else 0
  }

  override fun getSoftWeakReferenceIndex(): Int {
    return softWeakReferenceIndex
  }

  override fun getObjectSize(): Int {
    val localClass = currentClass ?: return idSize // size of null value

    return when {
      localClass.isPrimitiveArray() ->
        localClass.instanceSize + Type.getType(localClass.name).size * arraySize + ClassDefinition.ARRAY_PREAMBLE_SIZE
      localClass.isArray() -> localClass.instanceSize + idSize * arraySize + ClassDefinition.ARRAY_PREAMBLE_SIZE
      else -> localClass.instanceSize + ClassDefinition.OBJECT_PREAMBLE_SIZE
    }
  }

  override fun copyReferencesTo(outReferences: LongList) {
    outReferences.clear()
    outReferences.addAll(references)
  }

  override fun getRootReasonForObjectId(id: Long): RootReason? {
    var rootReason = roots[id]
    if (rootReason != null) {
      return rootReason
    }
    classStore.forEachClass { classDefinition ->
      if (classDefinition.id == id) {
        rootReason = RootReason.createClassDefinitionReason(classDefinition)
      }
      classDefinition.objectStaticFields.firstOrNull {
        it.value == id
      }?.let {
        rootReason = RootReason.createStaticFieldReferenceReason(classDefinition, it.name)
      }
      val index = classDefinition.constantFields.indexOfFirst {
        it == id
      }
      if (index != -1) {
        rootReason = RootReason.createConstantReferenceReason(classDefinition, index)
      }
    }
    return rootReason
  }
}

private fun readId(byteBuffer: ByteBuffer): Int {
  return readNonNegativeLEB128Int(byteBuffer)
}

private fun readNonNegativeLEB128Int(byteBuffer: ByteBuffer): Int {
  var v = 0
  var shift = 0
  while (true) {
    val b = byteBuffer.get()
    v = v or ((b and 0x7f).toInt() shl shift)
    if (b >= 0) {
      break
    }
    shift += 7
  }
  return v
}