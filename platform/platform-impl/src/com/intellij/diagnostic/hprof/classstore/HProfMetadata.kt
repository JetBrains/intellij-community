/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.intellij.diagnostic.hprof.classstore

import com.intellij.diagnostic.hprof.navigator.RootReason
import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.visitors.*
import gnu.trove.TLongObjectHashMap
import java.util.function.LongUnaryOperator

class HProfMetadata(var classStore: ClassStore, // TODO: private-set, public-get
                    val threads: TLongObjectHashMap<ThreadInfo>,
                    var roots: TLongObjectHashMap<RootReason>) {

  fun remapIds(remappingFunction: LongUnaryOperator) {
    // Remap ids in class store
    classStore = classStore.createStoreWithRemappedIDs(remappingFunction)

    // Remap root objects' ids
    val newRoots = TLongObjectHashMap<RootReason>()
    roots.forEachEntry { key, value ->
      val newKey = remappingFunction.applyAsLong(key)
      assert(!newRoots.containsKey(newKey))
      newRoots.put(newKey, value)
      true
    }
    roots = newRoots
  }

  companion object {
    fun create(parser: HProfEventBasedParser): HProfMetadata {
      val stringIdMap = TLongObjectHashMap<String>()
      val threadsMap = TLongObjectHashMap<ThreadInfo>()

      val classStoreVisitor = CreateClassStoreVisitor(stringIdMap)
      val threadInfoVisitor = CollectThreadInfoVisitor(threadsMap, stringIdMap)
      val rootReasonsVisitor = CollectRootReasonsVisitor(threadsMap)

      val visitor = CompositeVisitor(
        CollectStringValuesVisitor(stringIdMap),
        classStoreVisitor,
        threadInfoVisitor,
        rootReasonsVisitor
      )
      parser.accept(visitor, "create hprof metadata")
      return HProfMetadata(classStoreVisitor.getClassStore(),
                           threadsMap,
                           rootReasonsVisitor.roots)
    }
  }
}