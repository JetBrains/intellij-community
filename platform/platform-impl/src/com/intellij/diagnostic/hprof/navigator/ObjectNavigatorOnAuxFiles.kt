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

import com.intellij.diagnostic.hprof.parser.Type
import com.intellij.diagnostic.hprof.classstore.ClassDefinition
import com.intellij.diagnostic.hprof.classstore.ClassStore
import gnu.trove.TLongArrayList
import gnu.trove.TLongObjectHashMap
import java.nio.ByteBuffer
import kotlin.experimental.and

class ObjectNavigatorOnAuxFiles(
  private val roots: TLongObjectHashMap<RootReason>,
  private val auxOffsets: ByteBuffer,
  private val aux: ByteBuffer,
  classStore: ClassStore,
  instanceCount: Long
) : ObjectNavigator(classStore, instanceCount) {

  override fun getClass() = currentClass!!

  override fun getClassForObjectId(id: Long): ClassDefinition {
    auxOffsets.position((id * 4).toInt())
    aux.position(auxOffsets.int)
    val classId = aux.readId()
    return if (classId == 0) classStore.classClass else classStore[classId]
  }

  private var currentObjectId = 0L
  private var arraySize = 0
  private var currentClass: ClassDefinition? = null
  private val references = TLongArrayList()

  override val id: Long
    get() = currentObjectId

  override fun createRootsIterator(): Iterator<Long> {
    return object : Iterator<Long> {
      val internalIterator = roots.iterator()
      override fun hasNext(): Boolean {
        return internalIterator.hasNext()
      }

      override fun next(): Long {
        internalIterator.advance()
        return internalIterator.key()
      }
    }
  }

  override fun getReferencesCopy(): TLongArrayList {
    val result = TLongArrayList()
    for (i in 0 until references.size()) {
      result.add(references[i])
    }
    return result
  }

  override fun isNull(): Boolean {
    return id == 0L
  }

  override fun goTo(id: Long, referenceResolution: ReferenceResolution) {
    auxOffsets.position((id * 4).toInt())
    aux.position(auxOffsets.int)
    currentObjectId = id
    references.resetQuick()

    if (id == 0L) {
      currentClass = null
      return
    }
    val classId = aux.readId()
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

  private fun preloadPrimitiveArray() {
    arraySize = aux.readNonNegativeLEB128Int()
  }

  private fun preloadClass(classId: Int,
                           referenceResolution: ReferenceResolution) {
    arraySize = 0

    if (referenceResolution != ReferenceResolution.NO_REFERENCES) {
      val classDefinition = classStore[classId]
      classDefinition.constantFields.forEach(references::add)
      classDefinition.staticFields.forEach { references.add(it.objectId) }
    }
  }

  private fun preloadObjectArray(referenceResolution: ReferenceResolution) {
    val nullElementsCount = aux.readNonNegativeLEB128Int()
    val nonNullElementsCount = aux.readNonNegativeLEB128Int()

    arraySize = nullElementsCount + nonNullElementsCount

    if (referenceResolution != ReferenceResolution.NO_REFERENCES) {
      for (i in 0 until nonNullElementsCount) {
        references.add(aux.readId().toLong())
      }
    }
  }

  private fun preloadInstance(classDefinition: ClassDefinition,
                              referenceResolution: ReferenceResolution) {
    arraySize = 0

    if (referenceResolution == ReferenceResolution.NO_REFERENCES) {
      return
    }

    var c = classDefinition
    var isSoftOrWeakReference = false
    val includeSoftWeakReferences = referenceResolution == ReferenceResolution.ALL_REFERENCES
    do {
      isSoftOrWeakReference =
        isSoftOrWeakReference || classStore.isSoftOrWeakReferenceClass(c)
      val fields = c.refInstanceFields
      fields.forEach {
        val reference = aux.readId()
        if (!isSoftOrWeakReference || it.name != "referent" || includeSoftWeakReferences) {
          references.add(reference.toLong())
        }
        else {
          references.add(0L)
        }
      }
      val superClassId = c.superClassId
      if (superClassId == 0L) {
        break
      }
      c = classStore[superClassId]
    }
    while (true)
  }

  override fun getObjectSize(): Int {
    val localClass = currentClass ?: return REFERENCE_SIZE // size of null value

    return when {
      localClass.isPrimitiveArray() -> localClass.instanceSize + Type.getType(localClass.name).size * arraySize
      localClass.isArray() -> localClass.instanceSize + REFERENCE_SIZE * arraySize
      else -> localClass.instanceSize
    }
  }

  override fun copyReferencesTo(outReferences: TLongArrayList) {
    outReferences.resetQuick()
    outReferences.ensureCapacity(references.size())
    for (i in 0 until references.size()) {
      outReferences.add(references[i])
    }
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
      classDefinition.staticFields.firstOrNull {
        it.objectId == id
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

  private fun ByteBuffer.readId(): Int {
    return readNonNegativeLEB128Int()
  }

  private fun ByteBuffer.readNonNegativeLEB128Int(): Int {
    var v = 0
    var shift = 0
    while (true) {
      val b = get()
      v = v or ((b and 0x7f).toInt() shl shift)
      if (b >= 0) {
        break
      }
      shift += 7
    }
    return v
  }

  companion object {
    private const val REFERENCE_SIZE = 4
  }
}

