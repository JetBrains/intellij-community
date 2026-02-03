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
import com.intellij.diagnostic.hprof.util.IDMapper
import com.intellij.diagnostic.hprof.visitors.*
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HProfMetadata(var classStore: ClassStore, // TODO: private-set, public-get
                    val threads: Long2ObjectMap<ThreadInfo>,
                    var roots: Long2ObjectMap<RootReason>) {
  class RemapException : Exception()

  fun remapIds(idMapper: IDMapper) {
    // Remap ids in class store
    classStore = classStore.createStoreWithRemappedIDs(idMapper)

    // Remap root objects' ids
    val newRoots = Long2ObjectOpenHashMap<RootReason>()
    for (entry in Long2ObjectMaps.fastIterable(roots)) {
      try {
        val newKey = idMapper.getID(entry.longKey)
        if (newKey == 0L) {
          continue
        }
        assert(!newRoots.containsKey(newKey))
        newRoots.put(newKey, entry.value)
      } catch (e: RemapException) {
        // Ignore root entry if there is no associated object
      }
    }
    roots = newRoots
  }

  companion object {
    fun create(parser: HProfEventBasedParser): HProfMetadata {
      val stringIdMap = Long2ObjectOpenHashMap<String>()
      val threadsMap = Long2ObjectOpenHashMap<ThreadInfo>()

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