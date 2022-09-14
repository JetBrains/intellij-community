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
package com.intellij.diagnostic.hprof.analysis

import com.intellij.diagnostic.hprof.histogram.Histogram
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.util.IntList
import com.intellij.diagnostic.hprof.util.UByteList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList

class AnalysisContext(
  val navigator: ObjectNavigator,
  val config: AnalysisConfig,
  val parentList: IntList,
  val sizesList: IntList,
  val visitedList: IntList,
  val refIndexList: UByteList,
  var histogram: Histogram
) {
  val classStore = navigator.classStore
  val disposedObjectsIDs = IntOpenHashSet()
  val disposerParentToChildren = Long2ObjectOpenHashMap<LongArrayList>()
  var disposerTreeObjectId = 0
}