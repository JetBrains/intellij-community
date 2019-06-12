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

import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.visitors.CollectRootReasonsVisitor
import com.intellij.diagnostic.hprof.visitors.CompositeVisitor
import com.intellij.diagnostic.hprof.visitors.CreateAuxiliaryFilesVisitor
import com.intellij.diagnostic.hprof.classstore.ClassDefinition
import com.intellij.diagnostic.hprof.classstore.ClassStore
import gnu.trove.TLongArrayList
import java.nio.channels.FileChannel

abstract class ObjectNavigator(val classStore: ClassStore, val instanceCount: Long) {

  enum class ReferenceResolution {
    ALL_REFERENCES,
    ONLY_STRONG_REFERENCES,
    NO_REFERENCES
  }

  abstract val id: Long

  abstract fun createRootsIterator(): Iterator<Long>

  abstract fun goTo(id: Long, referenceResolution: ReferenceResolution = ReferenceResolution.ONLY_STRONG_REFERENCES)

  abstract fun getClass(): ClassDefinition

  abstract fun getReferencesCopy(): TLongArrayList
  abstract fun copyReferencesTo(outReferences: TLongArrayList)

  abstract fun getClassForObjectId(id: Long): ClassDefinition
  abstract fun getRootReasonForObjectId(id: Long): RootReason?

  abstract fun getObjectSize(): Int

  fun goToInstanceField(className: String?, fieldName: String) {
    val objectId = getInstanceFieldObjectId(className, fieldName)
    goTo(objectId, ReferenceResolution.ALL_REFERENCES)
  }

  fun getInstanceFieldObjectId(className: String?, name: String): Long {
    val refs = getReferencesCopy()
    className?.let {
      assert(className == getClass().name) { "Expected $className, got ${getClass().name}" }
    }
    val indexOfField = getClass().allRefFieldNames(classStore).indexOfFirst { it == name }
    return refs[indexOfField]
  }

  fun goToStaticField(className: String, fieldName: String) {
    val objectId = getStaticFieldObjectId(className, fieldName)
    goTo(objectId, ReferenceResolution.ALL_REFERENCES)
  }

  private fun getStaticFieldObjectId(className: String, fieldName: String) =
    classStore[className].staticFields.first { it.name == fieldName }.objectId


  companion object {
    fun createOnAuxiliaryFiles(parser: HProfEventBasedParser,
                               auxOffsetsChannel: FileChannel,
                               auxChannel: FileChannel,
                               classStore: ClassStore,
                               instanceCount: Long): ObjectNavigator {
      val collectRootReasonsVisitor = CollectRootReasonsVisitor()
      val createAuxiliaryFilesVisitor = CreateAuxiliaryFilesVisitor(auxOffsetsChannel, auxChannel, classStore, parser)

      val compositeVisitor = CompositeVisitor(
        collectRootReasonsVisitor,
        createAuxiliaryFilesVisitor
      )
      parser.accept(compositeVisitor, "roots/auxFiles")

      val roots = collectRootReasonsVisitor.roots

      val auxBuffer = auxChannel.map(FileChannel.MapMode.READ_ONLY, 0, auxChannel.size())
      val auxOffsetsBuffer =
        auxOffsetsChannel.map(FileChannel.MapMode.READ_ONLY, 0, auxOffsetsChannel.size())

      return ObjectNavigatorOnAuxFiles(roots, auxOffsetsBuffer, auxBuffer, classStore, instanceCount)
    }
  }

  abstract fun isNull(): Boolean
}

