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
import com.intellij.diagnostic.hprof.classstore.HProfMetadata
import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.visitors.CreateAuxiliaryFilesVisitor
import gnu.trove.TLongArrayList
import org.jetbrains.annotations.NonNls
import java.nio.channels.FileChannel

abstract class ObjectNavigator(val classStore: ClassStore, val instanceCount: Long) {

  enum class ReferenceResolution {
    ALL_REFERENCES,
    ONLY_STRONG_REFERENCES,
    NO_REFERENCES
  }

  data class RootObject(val id: Long, val reason: RootReason)

  abstract val id: Long

  abstract fun createRootsIterator(): Iterator<RootObject>

  abstract fun goTo(id: Long, referenceResolution: ReferenceResolution = ReferenceResolution.ONLY_STRONG_REFERENCES)

  abstract fun getClass(): ClassDefinition

  abstract fun getReferencesCopy(): TLongArrayList
  abstract fun copyReferencesTo(outReferences: TLongArrayList)

  abstract fun getClassForObjectId(id: Long): ClassDefinition
  abstract fun getRootReasonForObjectId(id: Long): RootReason?

  abstract fun getObjectSize(): Int

  abstract fun getSoftReferenceId(): Long
  abstract fun getWeakReferenceId(): Long
  abstract fun getSoftWeakReferenceIndex(): Int

  fun goToInstanceField(@NonNls className: String?, @NonNls fieldName: String) {
    val objectId = getInstanceFieldObjectId(className, fieldName)
    goTo(objectId, ReferenceResolution.ALL_REFERENCES)
  }

  fun getInstanceFieldObjectId(@NonNls className: String?, @NonNls name: String): Long {
    val refs = getReferencesCopy()
    className?.let {
      assert(className == getClass().name.substringBeforeLast('!')) { "Expected $className, got ${getClass().name}" }
    }
    val indexOfField = getClass().allRefFieldNames(classStore).indexOfFirst { it == name }
    return refs[indexOfField]
  }

  fun goToStaticField(@NonNls className: String, @NonNls fieldName: String) {
    val objectId = getStaticFieldObjectId(className, fieldName)
    goTo(objectId, ReferenceResolution.ALL_REFERENCES)
  }

  private fun getStaticFieldObjectId(className: String, fieldName: String) =
    classStore[className].objectStaticFields.first { it.name == fieldName }.value

  companion object {
    fun createOnAuxiliaryFiles(parser: HProfEventBasedParser,
                               auxOffsetsChannel: FileChannel,
                               auxChannel: FileChannel,
                               hprofMetadata: HProfMetadata,
                               instanceCount: Long): ObjectNavigator {
      val createAuxiliaryFilesVisitor = CreateAuxiliaryFilesVisitor(auxOffsetsChannel, auxChannel, hprofMetadata.classStore, parser)

      parser.accept(createAuxiliaryFilesVisitor, "auxFiles")

      val auxBuffer = auxChannel.map(FileChannel.MapMode.READ_ONLY, 0, auxChannel.size())
      val auxOffsetsBuffer =
        auxOffsetsChannel.map(FileChannel.MapMode.READ_ONLY, 0, auxOffsetsChannel.size())

      return ObjectNavigatorOnAuxFiles(hprofMetadata.roots, auxOffsetsBuffer, auxBuffer, hprofMetadata.classStore, instanceCount,
                                       parser.idSize)
    }
  }

  abstract fun isNull(): Boolean

  // Some objects may have additional data (varies by type). Only available when referenceResolution != NO_REFERENCES.
  abstract fun getExtraData(): Int

  abstract fun getStringInstanceFieldValue(): String?
}

